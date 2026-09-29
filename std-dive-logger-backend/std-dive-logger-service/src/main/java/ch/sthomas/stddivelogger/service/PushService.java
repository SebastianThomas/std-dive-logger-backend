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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Web Push: stores per-browser subscriptions and fans payloads out to them via {@link
 * WebPushSender}. Sending never holds a DB transaction: targets are read, sent concurrently
 * (bounded), then the outcomes are written back in one short transaction.
 */
@Service
public class PushService {

    private static final Logger logger = LoggerFactory.getLogger(PushService.class);
    static final int MAX_CONCURRENT_SENDS = 16;
    // A subscription failing this often with no success for this long is dead, not flaky.
    private static final int PRUNE_AFTER_FAILURES = 10;
    private static final Duration PRUNE_AFTER_SILENCE = Duration.ofDays(30);

    private final PushSubscriptionRepository repository;
    private final WebPushSender sender;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;
    private final String vapidPublicKey;

    public PushService(
            final PushSubscriptionRepository repository,
            final WebPushSender sender,
            final JsonMapper jsonMapper,
            final PlatformTransactionManager transactionManager,
            @Value("${ch.sthomas.stddivelogger.push.vapid.public-key:}")
                    final String vapidPublicKey) {
        this.repository = repository;
        this.sender = sender;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.vapidPublicKey = vapidPublicKey;
    }

    /** The VAPID public key the browser needs to {@code pushManager.subscribe()}; "" if unset. */
    public String publicKey() {
        return vapidPublicKey;
    }

    /**
     * Whether browsers may subscribe. Only the public key matters here: {@code ws} serves it but
     * never sends - the private key lives only in {@code analytics}, the one deployable that does.
     */
    public boolean isEnabled() {
        return !vapidPublicKey.isBlank();
    }

    /** Upserts by endpoint; the owner follows the latest subscriber. Returns the sync choice. */
    @Transactional
    public boolean subscribe(
            final long userId,
            final PushSubscriptionRequest request,
            final @Nullable String userAgent) {
        final var subscription =
                repository
                        .findByEndpoint(request.endpoint())
                        .map(
                                existing ->
                                        existing.refresh(
                                                userId,
                                                request.keys().p256dh(),
                                                request.keys().auth(),
                                                userAgent))
                        .orElseGet(
                                () ->
                                        repository.save(
                                                new PushSubscriptionEntity(
                                                        userId,
                                                        request.endpoint(),
                                                        request.keys().p256dh(),
                                                        request.keys().auth(),
                                                        userAgent)));
        if (request.logbookSync() != null) {
            subscription.setLogbookSync(request.logbookSync());
        }
        return subscription.isLogbookSync();
    }

    @Transactional
    public void unsubscribe(final long userId, final String endpoint) {
        repository
                .findByEndpoint(endpoint)
                .filter(s -> s.getUserId() == userId)
                .ifPresent(repository::delete);
    }

    /** Part of revoking every session of an account (password change, logout everywhere). */
    @Transactional
    public int deleteAllForUser(final long userId) {
        return repository.deleteAllByUserId(userId);
    }

    /** Nightly: drop subscriptions that keep failing and haven't delivered in a month. */
    @Transactional
    public int pruneFailing() {
        final var cutoff = Instant.now().minus(PRUNE_AFTER_SILENCE);
        return repository.deleteFailing(PRUNE_AFTER_FAILURES, cutoff);
    }

    /** Delivers a reminder notification to every browser the user has enabled. */
    public int sendToUser(final long userId, final WebPushMessage message) {
        final var payload = jsonMapper.writeValueAsBytes(message);
        return fanOut(repository.findByUserId(userId), _ -> payload, PushOptions.REMINDER);
    }

    /**
     * Sends a per-subscription payload to every target concurrently (at most {@value
     * #MAX_CONCURRENT_SENDS} in flight), then records the outcomes: 404/410 deletes the
     * subscription, success/failure update its counters. Returns how many were delivered.
     */
    public int fanOut(
            final List<PushSubscriptionEntity> targets,
            final Function<PushSubscriptionEntity, byte[]> payload,
            final PushOptions options) {
        if (targets.isEmpty()) {
            return 0;
        }
        final var outcomes = new ArrayList<Outcome>(targets.size());
        final var permits = new Semaphore(MAX_CONCURRENT_SENDS);
        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var futures = new ArrayList<Future<Outcome>>(targets.size());
            for (final var target : targets) {
                futures.add(
                        executor.submit(
                                () -> {
                                    permits.acquire();
                                    try {
                                        return new Outcome(
                                                target.getId(), safeSend(target, payload, options));
                                    } finally {
                                        permits.release();
                                    }
                                }));
            }
            for (final var future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final java.util.concurrent.ExecutionException e) {
                    logger.warn("Web push task failed", e.getCause());
                }
            }
        }
        if (!outcomes.isEmpty()) {
            transaction.executeWithoutResult(_ -> applyOutcomes(outcomes));
        }
        return (int) outcomes.stream().filter(o -> o.result() == PushSendResult.SENT).count();
    }

    private void applyOutcomes(final List<Outcome> outcomes) {
        final var resultById =
                outcomes.stream()
                        .collect(Collectors.toMap(Outcome::subscriptionId, Outcome::result));
        for (final var subscription : repository.findAllById(resultById.keySet())) {
            switch (resultById.getOrDefault(subscription.getId(), PushSendResult.NOT_CONFIGURED)) {
                case SENT -> subscription.recordSuccess();
                case GONE -> repository.delete(subscription);
                case FAILED -> subscription.recordFailure();
                case NOT_CONFIGURED -> {
                    /* nothing to do - push isn't set up in this deployable */
                }
            }
        }
    }

    private PushSendResult safeSend(
            final PushSubscriptionEntity subscription,
            final Function<PushSubscriptionEntity, byte[]> payload,
            final PushOptions options) {
        try {
            return sender.send(subscription, payload.apply(subscription), options);
        } catch (final RuntimeException e) {
            logger.warn("Web push to subscription {} failed", subscription.getId(), e);
            return PushSendResult.FAILED;
        }
    }

    private record Outcome(Long subscriptionId, PushSendResult result) {}
}
