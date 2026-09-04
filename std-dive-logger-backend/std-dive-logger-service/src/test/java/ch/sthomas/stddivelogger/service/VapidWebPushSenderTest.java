package ch.sthomas.stddivelogger.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;
import ch.sthomas.stddivelogger.model.push.WebPushMessage;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import java.util.Base64;

/**
 * No real push service to test against here, so this covers what we *can* verify without one: the
 * VAPID-JWT-signing + RFC-8291-encryption path (via {@code AbstractPushService.prepareRequest})
 * runs to completion without throwing given well-formed keys, and the sender degrades cleanly
 * (never throwing) when unconfigured, misconfigured, or the HTTP send itself fails.
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

    private static final tools.jackson.databind.json.JsonMapper JSON_MAPPER =
            ObjectMapperUtils.objectMapperBuilder(_ -> {}).build();

    private static PushSubscriptionEntity subscription(final String endpoint) {
        // 16 zero bytes is not a *real* auth secret, but it's the right shape (base64url, 16
        // bytes) - enough to exercise HKDF/AES-128-GCM without needing a live subscriber.
        final var auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        return new PushSubscriptionEntity(1L, endpoint, SUBSCRIBER_PUBLIC_KEY, auth, "test-agent");
    }

    private static WebPushMessage message() {
        return WebPushMessage.of("Time to go diving again", "It's been 3 weeks.", "/dives/map");
    }

    @Test
    void notConfiguredWhenAnyVapidPropertyIsBlank() {
        final var sender = new VapidWebPushSender(JSON_MAPPER, "", APP_PRIVATE_KEY, SUBJECT);
        assertThat(sender.isConfigured()).isFalse();
        assertThat(sender.send(subscription("https://push.example/x"), message()))
                .isEqualTo(PushSendResult.NOT_CONFIGURED);
    }

    @Test
    void staysUnconfiguredRatherThanThrowingWhenKeysAreMalformed() {
        final var sender =
                new VapidWebPushSender(JSON_MAPPER, "not-a-key", "also-not-a-key", SUBJECT);
        assertThat(sender.isConfigured()).isFalse();
    }

    @Test
    void buildsAndAttemptsARealVapidRequestThenFailsGracefullyOnAConnectionError() {
        final var sender =
                new VapidWebPushSender(JSON_MAPPER, APP_PUBLIC_KEY, APP_PRIVATE_KEY, SUBJECT);
        assertThat(sender.isConfigured()).isTrue();

        // Nothing listens on this port - the VAPID/encryption step must succeed first (or we'd
        // never reach the HTTP call), and the subsequent connection failure is reported as FAILED,
        // not an exception escaping to the caller.
        final var result =
                sender.send(subscription("https://localhost:1/fake-endpoint"), message());

        assertThat(result).isEqualTo(PushSendResult.FAILED);
    }
}
