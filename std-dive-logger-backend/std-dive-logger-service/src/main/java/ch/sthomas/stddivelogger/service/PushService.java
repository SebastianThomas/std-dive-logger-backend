package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;
import ch.sthomas.stddivelogger.model.push.PushSubscriptionRequest;
import ch.sthomas.stddivelogger.model.push.WebPushMessage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web Push: stores per-browser subscriptions and fans a {@link WebPushMessage} out to a user's
 * devices via {@link WebPushSender}.
 */
@Service
public class PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final String vapidPublicKey;

    public PushService(
            final PushSubscriptionRepository repository,
            final WebPushSender sender,
            @Value("${ch.sthomas.stddivelogger.push.vapid.public-key:}")
                    final String vapidPublicKey) {
        this.repository = repository;
        this.sender = sender;
        this.vapidPublicKey = vapidPublicKey;
    }

    /** The VAPID public key the browser needs to {@code pushManager.subscribe()}; "" if unset. */
    public String publicKey() {
        return vapidPublicKey;
    }

    public boolean isEnabled() {
        return !vapidPublicKey.isBlank() && sender.isConfigured();
    }

    @Transactional
    public void subscribe(
            final long userId,
            final PushSubscriptionRequest request,
            final @Nullable String userAgent) {
        repository
                .findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing ->
                                existing.refresh(
                                        request.keys().p256dh(), request.keys().auth(), userAgent),
                        () ->
                                repository.save(
                                        new PushSubscriptionEntity(
                                                userId,
                                                request.endpoint(),
                                                request.keys().p256dh(),
                                                request.keys().auth(),
                                                userAgent)));
    }

    @Transactional
    public void unsubscribe(final long userId, final String endpoint) {
        repository
                .findByEndpoint(endpoint)
                .filter(s -> s.getUserId() == userId)
                .ifPresent(repository::delete);
    }

    /** Delivers to every browser the user has enabled, pruning endpoints reported gone. */
    @Transactional
    public int sendToUser(final long userId, final WebPushMessage message) {
        final var subscriptions = repository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (final var subscription : subscriptions) {
            final PushSendResult result = safeSend(subscription, message);
            switch (result) {
                case SENT -> {
                    subscription.recordSuccess();
                    sent++;
                }
                case GONE -> repository.delete(subscription);
                case FAILED -> subscription.recordFailure();
                case NOT_CONFIGURED -> {
                    /* nothing to do - push isn't set up yet */
                }
            }
        }
        return sent;
    }

    private PushSendResult safeSend(
            final PushSubscriptionEntity subscription, final WebPushMessage message) {
        try {
            return sender.send(subscription, message);
        } catch (final RuntimeException e) {
            logger.warn("Web push to subscription {} failed", subscription.getId(), e);
            return PushSendResult.FAILED;
        }
    }
}
