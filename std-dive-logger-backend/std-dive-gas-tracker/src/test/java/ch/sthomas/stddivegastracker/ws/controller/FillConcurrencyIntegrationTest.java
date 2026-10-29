package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** DOMAIN §5.6 under concurrency: the transfer graph stays a DAG whatever the interleaving. */
final class FillConcurrencyIntegrationTest extends AbstractFillIntegrationTest {

    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void twoOppositeTransfersRecordedAtOnceLeaveExactlyOneAndTheTeamsBanksStillLoad()
            throws Exception {
        final var room = room();
        final var a = nitroxBank(room, "EAN32");
        final var b = nitroxBank(room, "EAN28");
        final var aIntoB =
                at(
                        fillBody(
                                bankTarget(b.vesselId()),
                                0,
                                fromBank(a.vesselId(), 30),
                                fromAir(150)),
                        day(1));
        final var bIntoA =
                at(
                        fillBody(
                                bankTarget(a.vesselId()),
                                0,
                                fromBank(b.vesselId(), 30),
                                fromAir(150)),
                        day(1));
        try (final var pool = Executors.newFixedThreadPool(2)) {
            // Hold the team row, so both requests pass the service's snapshot check (neither
            // transfer is stored yet) and then queue on the lock in the data service; releasing it
            // lets them run one after the other, the second one against what the first committed.
            final var calls =
                    new TransactionTemplate(transactionManager)
                            .execute(
                                    _ -> {
                                        jdbc.queryForList(
                                                "SELECT pk_team_id FROM t_team"
                                                        + " WHERE pk_team_id = ? FOR UPDATE",
                                                room.teamId());
                                        final var started =
                                                List.of(
                                                        pool.submit(
                                                                post(
                                                                        room.adminEmail(),
                                                                        room.teamId(),
                                                                        aIntoB)),
                                                        pool.submit(
                                                                post(
                                                                        room.adminEmail(),
                                                                        room.teamId(),
                                                                        bIntoA)));
                                        awaitLockWaiters(2);
                                        return started;
                                    });

            final var started = Objects.requireNonNull(calls);
            final var statuses =
                    List.of(
                            started.get(0).get(30, TimeUnit.SECONDS),
                            started.get(1).get(30, TimeUnit.SECONDS));

            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
            assertThat(
                            listFills(room.adminEmail(), room.teamId(), "?includeVoided=true")
                                    .totalElements())
                    .isEqualTo(1);
            // the engine would throw on a cycle: the banks list and both bank details must load
            assertThat(
                            vesselRequest(
                                            HttpMethod.GET,
                                            room.adminEmail(),
                                            room.teamId(),
                                            "?status=ALL",
                                            null)
                                    .returnResult(String.class)
                                    .getStatus()
                                    .value())
                    .isEqualTo(200);
            getVessel(room.adminEmail(), room.teamId(), a.vesselId());
            getVessel(room.adminEmail(), room.teamId(), b.vesselId());
        }
    }

    private Callable<Integer> post(
            final String email, final long teamId, final Map<String, Object> body) {
        return () ->
                fillRequest(HttpMethod.POST, email, teamId, "", body)
                        .returnResult(String.class)
                        .getStatus()
                        .value();
    }

    /**
     * Waits until {@code count} sessions are blocked on a lock (the requests, queued on the team).
     */
    private void awaitLockWaiters(final int count) {
        final var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            // pg_stat_activity is cached per transaction; this one is the lock holder's
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            final var waiting =
                    count(
                            "SELECT count(*) FROM pg_stat_activity"
                                    + " WHERE wait_event_type = 'Lock' AND datname = current_database()");
            if (waiting >= count) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        throw new AssertionError("The two requests never queued on the team lock");
    }

    private VesselDetail nitroxBank(final Room room, final String label) {
        return createVessel(
                room.adminEmail(),
                room.teamId(),
                with(
                        with(bank(label, EAN32), "waterVolumeLitres", 100),
                        "openedAt",
                        T0.toEpochMilli()));
    }
}
