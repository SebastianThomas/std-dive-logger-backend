package ch.sthomas.stddivelogger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;
import ch.sthomas.stddivelogger.model.push.PushSubscriptionRequest;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

class PushServiceTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private PushService service(final WebPushSender sender, final String publicKey) {
        return new PushService(
                repository,
                sender,
                ObjectMapperUtils.objectMapperBuilder(_ -> {}).build(),
                transactions,
                publicKey);
    }

    private static PushSubscriptionEntity subscription(final long id, final String endpoint) {
        final var entity = new PushSubscriptionEntity(7L, endpoint, "p256dh", "auth", null);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    @Test
    void enabledNeedsOnlyThePublicKeyBecauseWsNeverSends() {
        final var unconfiguredSender = mock(WebPushSender.class);
        when(unconfiguredSender.isConfigured()).thenReturn(false);
        assertThat(service(unconfiguredSender, "BPublicKey").isEnabled()).isTrue();
        assertThat(service(unconfiguredSender, "").isEnabled()).isFalse();
    }

    @Test
    void reSubscribingFromAnotherAccountMovesTheEndpointToThatAccount() {
        final var existing = subscription(1, "https://fcm.googleapis.com/fcm/send/shared");
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));

        final boolean sync =
                service(mock(WebPushSender.class), "k")
                        .subscribe(
                                42L,
                                new PushSubscriptionRequest(
                                        existing.getEndpoint(),
                                        new PushSubscriptionRequest.Keys("p2", "a2"),
                                        null),
                                "ua");

        assertThat(existing.getUserId()).isEqualTo(42L);
        assertThat(existing.getP256dh()).isEqualTo("p2");
        assertThat(sync).isTrue();
    }

    @Test
    void logbookSyncChoiceIsStoredAndNullKeepsIt() {
        final var existing = subscription(1, "https://fcm.googleapis.com/fcm/send/x");
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        final var push = service(mock(WebPushSender.class), "k");
        final var keys = new PushSubscriptionRequest.Keys("p", "a");

        assertThat(
                        push.subscribe(
                                7L,
                                new PushSubscriptionRequest(existing.getEndpoint(), keys, false),
                                null))
                .isFalse();
        assertThat(
                        push.subscribe(
                                7L,
                                new PushSubscriptionRequest(existing.getEndpoint(), keys, null),
                                null))
                .isFalse();
    }

    @Test
    void fanOutRecordsOutcomesAndPrunesGoneEndpointsAfterSending() {
        final var ok = subscription(1, "https://push.example/ok");
        final var gone = subscription(2, "https://push.example/gone");
        final var failing = subscription(3, "https://push.example/failing");
        when(repository.findAllById(anyCollection())).thenReturn(List.of(ok, gone, failing));
        final var sendsDone = new AtomicInteger();
        final var transactionOpenedEarly = new AtomicBoolean();
        when(transactions.getTransaction(any()))
                .thenAnswer(
                        _ -> {
                            // Outcomes are written only once every HTTP call has finished.
                            transactionOpenedEarly.set(sendsDone.get() < 3);
                            return null;
                        });
        final WebPushSender sender =
                fakeSender(
                        (subscription, _) -> {
                            sendsDone.incrementAndGet();
                            return switch (subscription.getEndpoint()) {
                                case "https://push.example/ok" -> PushSendResult.SENT;
                                case "https://push.example/gone" -> PushSendResult.GONE;
                                default -> PushSendResult.FAILED;
                            };
                        });

        final int delivered =
                service(sender, "k")
                        .fanOut(
                                List.of(ok, gone, failing),
                                s -> s.getEndpoint().getBytes(StandardCharsets.UTF_8),
                                PushOptions.LOGBOOK_SYNC);

        assertThat(delivered).isEqualTo(1);
        assertThat(transactionOpenedEarly).isFalse();
        verify(repository).delete(gone);
        verify(repository, never()).delete(ok);
        assertThat(failing.getFailureCount()).isEqualTo(1);
    }

    @Test
    void fanOutSendsConcurrentlyButBounded() {
        final var inFlight = new AtomicInteger();
        final var maxInFlight = new AtomicInteger();
        final WebPushSender sender =
                fakeSender(
                        (_, _) -> {
                            final int now = inFlight.incrementAndGet();
                            maxInFlight.accumulateAndGet(now, Math::max);
                            try {
                                Thread.sleep(100);
                            } catch (final InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            inFlight.decrementAndGet();
                            return PushSendResult.SENT;
                        });
        final var targets =
                new ArrayList<>(
                        IntStream.range(0, 40)
                                .mapToObj(i -> subscription(i, "https://push.example/" + i))
                                .toList());
        when(repository.findAllById(anyCollection())).thenReturn(targets);

        final long start = System.nanoTime();
        final int delivered =
                service(sender, "k").fanOut(targets, _ -> new byte[] {1}, PushOptions.REMINDER);
        final long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(delivered).isEqualTo(40);
        assertThat(maxInFlight.get()).isGreaterThan(1).isLessThanOrEqualTo(16);
        // Sequential would be 40 x 100 ms.
        assertThat(elapsedMillis).isLessThan(2_000);
    }

    @Test
    void aThrowingSenderCountsAsAFailureNotAnEscapedException() {
        final var target = subscription(1, "https://push.example/boom");
        when(repository.findAllById(anyCollection())).thenReturn(List.of(target));
        final WebPushSender sender =
                fakeSender(
                        (_, _) -> {
                            throw new IllegalStateException("boom");
                        });

        final int delivered =
                service(sender, "k")
                        .fanOut(List.of(target), _ -> new byte[0], PushOptions.REMINDER);

        assertThat(delivered).isZero();
        assertThat(target.getFailureCount()).isEqualTo(1);
    }

    private interface SendFunction {
        PushSendResult send(PushSubscriptionEntity subscription, byte[] payload);
    }

    private static WebPushSender fakeSender(final SendFunction function) {
        return new WebPushSender() {
            @Override
            public PushSendResult send(
                    final PushSubscriptionEntity subscription,
                    final byte[] payload,
                    final PushOptions options) {
                return function.send(subscription, payload);
            }

            @Override
            public boolean isConfigured() {
                return true;
            }
        };
    }
}
