package ch.sthomas.stddivelogger.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * No real push service to test against here, so this covers what we *can* verify without one: the
 * VAPID-JWT-signing + RFC-8291-encryption path runs to completion given well-formed keys, the
 * delivery headers (TTL / Urgency / Topic) reach the wire, status codes map to results, and the
 * sender degrades cleanly (never throwing) when unconfigured or the HTTP send itself fails.
 */
class VapidWebPushSenderTest {

    // A throwaway VAPID-format keypair (nl.martijndwars:web-push CLI), used only as fixtures here
    // - never the app's real key. VAPID and subscriber (p256dh) keys share the same P-256 /
    // uncompressed-point / base64url format, so the same generator works for both roles below.
    private static final String APP_PUBLIC_KEY =
            "BJK5XxesUUDpFLiHuAdrFF6uy_da5zynsYdz9-EinAuaBehZ4SkLjuoNLZuvUu6M4x5OZTzGOq_6f11AwjYQIZ0";
    private static final String APP_PRIVATE_KEY = "iabc14_52yd19lrbH_49pNrQPkoDcop7WS0AlDNimhI";
    private static final String SUBSCRIBER_PUBLIC_KEY =
            "BDCIgyZN3Kzv5-akfNdvdyoha3Th_Qbg98kf4UHJ_jOfkuoHmPQv9rCKsgNjy1K2KpJ99ToU3WzfjffZ_IGt_gM";
    private static final String SUBJECT = "mailto:test@test.ch";
    private static final byte[] PAYLOAD =
            "{\"title\":\"Time to go diving again\"}".getBytes(StandardCharsets.UTF_8);

    private static PushSubscriptionEntity subscription(final String endpoint) {
        // 16 zero bytes is not a *real* auth secret, but it's the right shape (base64url, 16
        // bytes) - enough to exercise HKDF/AES-128-GCM without needing a live subscriber.
        final var auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        return new PushSubscriptionEntity(1L, endpoint, SUBSCRIBER_PUBLIC_KEY, auth, "test-agent");
    }

    private static VapidWebPushSender configuredSender() {
        return new VapidWebPushSender(APP_PUBLIC_KEY, APP_PRIVATE_KEY, SUBJECT);
    }

    @Test
    void notConfiguredWhenAnyVapidPropertyIsBlank() {
        final var sender = new VapidWebPushSender("", APP_PRIVATE_KEY, SUBJECT);
        assertThat(sender.isConfigured()).isFalse();
        assertThat(
                        sender.send(
                                subscription("https://push.example/x"),
                                PAYLOAD,
                                PushOptions.REMINDER))
                .isEqualTo(PushSendResult.NOT_CONFIGURED);
    }

    @Test
    void staysUnconfiguredRatherThanThrowingWhenKeysAreMalformed() {
        final var sender = new VapidWebPushSender("not-a-key", "also-not-a-key", SUBJECT);
        assertThat(sender.isConfigured()).isFalse();
    }

    @Test
    void buildsAndAttemptsARealVapidRequestThenFailsGracefullyOnAConnectionError() {
        final var sender = configuredSender();
        assertThat(sender.isConfigured()).isTrue();

        // Nothing listens on this port - the VAPID/encryption step must succeed first (or we'd
        // never reach the HTTP call), and the subsequent connection failure is reported as FAILED,
        // not an exception escaping to the caller.
        final var result =
                sender.send(
                        subscription("https://localhost:1/fake-endpoint"),
                        PAYLOAD,
                        PushOptions.REMINDER);

        assertThat(result).isEqualTo(PushSendResult.FAILED);
    }

    @Test
    void sendsEncryptedPayloadWithTopicUrgencyAndTtlHeaders() throws IOException {
        final var received = new AtomicReference<Headers>();
        final var bodyLength = new AtomicReference<Integer>();
        final var server = localPushService(201, received, bodyLength);
        try {
            final var result =
                    configuredSender()
                            .send(
                                    subscription(endpoint(server)),
                                    PAYLOAD,
                                    PushOptions.LOGBOOK_SYNC);

            assertThat(result).isEqualTo(PushSendResult.SENT);
            final var headers = received.get();
            assertThat(headers.getFirst("Topic")).isEqualTo("logbook-sync");
            assertThat(headers.getFirst("Urgency")).isEqualTo("normal");
            assertThat(headers.getFirst("TTL")).isEqualTo(String.valueOf(3 * 24 * 3600));
            assertThat(headers.getFirst("Content-Encoding")).isEqualTo("aes128gcm");
            assertThat(headers.getFirst("Authorization")).startsWith("vapid t=");
            // Encrypted record: never the plaintext bytes.
            assertThat(bodyLength.get()).isGreaterThan(PAYLOAD.length);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reminderHasNoTopicAndGoneEndpointsAreReportedAsGone() throws IOException {
        final var received = new AtomicReference<Headers>();
        final var server = localPushService(410, received, new AtomicReference<>());
        try {
            final var result =
                    configuredSender()
                            .send(subscription(endpoint(server)), PAYLOAD, PushOptions.REMINDER);

            assertThat(result).isEqualTo(PushSendResult.GONE);
            assertThat(received.get().getFirst("Topic")).isNull();
            assertThat(received.get().getFirst("TTL")).isEqualTo(String.valueOf(24 * 3600));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer localPushService(
            final int status,
            final AtomicReference<Headers> headers,
            final AtomicReference<Integer> bodyLength)
            throws IOException {
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
                "/push",
                exchange -> {
                    headers.set(exchange.getRequestHeaders());
                    bodyLength.set(exchange.getRequestBody().readAllBytes().length);
                    exchange.sendResponseHeaders(status, -1);
                    exchange.close();
                });
        server.start();
        return server;
    }

    private static String endpoint(final HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + "/push/device-1";
    }
}
