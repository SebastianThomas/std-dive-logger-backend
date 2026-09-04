package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;
import ch.sthomas.stddivelogger.model.push.WebPushMessage;

import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Urgency;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;

/**
 * VAPID-signs and RFC-8291-encrypts via {@code nl.martijndwars:web-push} (see {@link
 * LiteWebPushService}), then sends with the JDK's own {@link HttpClient} instead of the library's
 * bundled transports. Degrades to {@link PushSendResult#NOT_CONFIGURED} when the VAPID properties
 * are unset or malformed, rather than failing startup.
 */
@Service
public class VapidWebPushSender implements WebPushSender {

    private static final Logger logger = LoggerFactory.getLogger(VapidWebPushSender.class);
    private static final int TTL_SECONDS = (int) Duration.ofDays(1).toSeconds();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final @Nullable LiteWebPushService pushService;

    public VapidWebPushSender(
            final JsonMapper jsonMapper,
            @Value("${ch.sthomas.stddivelogger.push.vapid.public-key:}") final String publicKey,
            @Value("${ch.sthomas.stddivelogger.push.vapid.private-key:}") final String privateKey,
            @Value("${ch.sthomas.stddivelogger.push.vapid.subject:}") final String subject) {
        this.jsonMapper = jsonMapper;
        this.pushService = buildPushService(publicKey, privateKey, subject);
    }

    private static @Nullable LiteWebPushService buildPushService(
            final String publicKey, final String privateKey, final String subject) {
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            return null;
        }
        try {
            return new LiteWebPushService(publicKey, privateKey, subject);
        } catch (final GeneralSecurityException | IllegalArgumentException e) {
            logger.warn("Configured VAPID keys are invalid - web push disabled.", e);
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return pushService != null;
    }

    @Override
    public PushSendResult send(
            final PushSubscriptionEntity subscription, final WebPushMessage message) {
        if (pushService == null) {
            return PushSendResult.NOT_CONFIGURED;
        }
        try {
            final var notification =
                    Notification.builder()
                            .endpoint(subscription.getEndpoint())
                            .userPublicKey(subscription.getP256dh())
                            .userAuth(subscription.getAuth())
                            .payload(jsonMapper.writeValueAsBytes(message))
                            .urgency(Urgency.NORMAL)
                            .ttl(TTL_SECONDS)
                            .build();
            return dispatch(pushService.buildRequest(notification));
        } catch (final GeneralSecurityException | IOException | JoseException e) {
            logger.warn(
                    "Failed to build web push request for subscription {}",
                    subscription.getId(),
                    e);
            return PushSendResult.FAILED;
        }
    }

    private PushSendResult dispatch(final nl.martijndwars.webpush.HttpRequest request) {
        var builder =
                java.net.http.HttpRequest.newBuilder(URI.create(request.getUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .POST(
                                java.net.http.HttpRequest.BodyPublishers.ofByteArray(
                                        request.getBody()));
        for (final var header : request.getHeaders().entrySet()) {
            builder = builder.header(header.getKey(), header.getValue());
        }
        try {
            final var response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            return switch (response.statusCode()) {
                case 200, 201, 202 -> PushSendResult.SENT;
                case 404, 410 -> PushSendResult.GONE;
                default -> {
                    logger.info(
                            "Web push service responded {} for endpoint ending …{}",
                            response.statusCode(),
                            tail(request.getUrl()));
                    yield PushSendResult.FAILED;
                }
            };
        } catch (final IOException e) {
            logger.warn("Web push HTTP send failed", e);
            return PushSendResult.FAILED;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushSendResult.FAILED;
        }
    }

    private static String tail(final String endpoint) {
        return endpoint.length() <= 12 ? endpoint : endpoint.substring(endpoint.length() - 12);
    }

    /**
     * Exposes the library's protected {@code prepareRequest} without its bundled HTTP transport.
     */
    private static final class LiteWebPushService extends AbstractPushService<LiteWebPushService> {
        LiteWebPushService(final String publicKey, final String privateKey, final String subject)
                throws GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }

        nl.martijndwars.webpush.HttpRequest buildRequest(final Notification notification)
                throws GeneralSecurityException, IOException, JoseException {
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }
}
