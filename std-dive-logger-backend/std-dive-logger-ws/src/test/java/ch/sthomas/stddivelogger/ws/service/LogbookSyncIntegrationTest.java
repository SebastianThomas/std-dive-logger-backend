package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.HomeDataService;
import ch.sthomas.stddivelogger.data.service.LogbookSyncDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Change detection behind the logbook-sync push ({@link LogbookSyncDataService}): silent first
 * sighting, trailing debounce, minimum interval, and only divers with a sync-enabled device and a
 * live session.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class LogbookSyncIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-secret",
                () -> "logbook-sync-it-jwt-signing-secret-needs-to-be-long-enough");
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret",
                () -> "logbook-sync-it-jwt-refresh-secret-that-is-comfortably-over-48-chars");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    // Other test classes share the reused container: scan everything, then look for our divers.
    private static final int ALL = 100_000;

    @Autowired private LogbookSyncDataService syncData;
    @Autowired private HomeDataService homeData;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private PushSubscriptionRepository pushSubscriptions;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private DiveService diveService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private User seedDiver(final boolean liveSession, final boolean logbookSync) {
        final var user =
                userRepository
                        .save(
                                new UserEntity(
                                        "sync-it-" + UUID.randomUUID() + "@test.ch",
                                        "h",
                                        "Sync " + UUID.randomUUID()))
                        .toRecord();
        refreshTokens.save(
                new RefreshTokenEntity(
                        UUID.randomUUID().toString(),
                        user.id(),
                        Instant.now()
                                .plus(liveSession ? Duration.ofDays(1) : Duration.ofDays(-1))));
        final var subscription =
                new PushSubscriptionEntity(
                        user.id(),
                        "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(),
                        "p",
                        "a",
                        null);
        subscription.setLogbookSync(logbookSync);
        pushSubscriptions.save(subscription);
        return user;
    }

    private void logDive(final User diver, final int number) {
        final long siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Sync IT " + UUID.randomUUID(),
                                        new Location(46.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
        diveService.createEmptyDive(
                diver,
                new UploadDiveBody(
                        number,
                        "sync-it",
                        siteId,
                        18.0 + number,
                        Duration.ofMinutes(40),
                        Instant.parse("2026-06-01T09:00:00Z").plus(Duration.ofDays(number))));
        entityManager.flush();
    }

    /** Moves every dive change of the diver past the quiet period. */
    private void settle(final User diver) {
        entityManager.flush();
        jdbc.update(
                "UPDATE t_dives SET updated_at = now() - interval '5 minutes' WHERE fk_diver_id = ?",
                diver.id());
        entityManager.clear();
    }

    private Optional<LogbookSyncDataService.Due> due(final User diver) {
        return syncData.findDue(ALL).stream().filter(d -> d.userId() == diver.id()).findFirst();
    }

    @Test
    void firstSightingIsSeededAndNothingIsDueUntilTheLogbookChanges() {
        final var diver = seedDiver(true, true);
        logDive(diver, 1);
        settle(diver);

        final var first = due(diver).orElseThrow();
        assertThat(first.known()).isFalse();
        syncData.seed(diver.id(), first.fingerprint());

        assertThat(due(diver)).isEmpty();
    }

    @Test
    void aChangeIsDueOnlyAfterItHasSettled() {
        final var diver = seedDiver(true, true);
        logDive(diver, 1);
        settle(diver);
        syncData.seed(diver.id(), due(diver).orElseThrow().fingerprint());

        logDive(diver, 2);
        assertThat(due(diver)).as("still inside the quiet period").isEmpty();

        settle(diver);
        final var changed = due(diver).orElseThrow();
        assertThat(changed.known()).isTrue();

        syncData.markSynced(diver.id(), changed.fingerprint());
        assertThat(due(diver)).isEmpty();
    }

    @Test
    void atMostOnePushPerMinimumInterval() {
        final var diver = seedDiver(true, true);
        logDive(diver, 1);
        settle(diver);
        syncData.markSynced(diver.id(), "an-older-fingerprint");

        logDive(diver, 2);
        settle(diver);
        assertThat(due(diver)).as("pushed moments ago").isEmpty();

        jdbc.update(
                "UPDATE t_logbook_sync_state SET sent_at = now() - interval '11 minutes'"
                        + " WHERE fk_user_id = ?",
                diver.id());
        assertThat(due(diver)).isPresent();
    }

    @Test
    void diversWithoutALiveSessionOrWithSyncTurnedOffAreNeverScanned() {
        final var loggedOut = seedDiver(false, true);
        final var optedOut = seedDiver(true, false);
        logDive(loggedOut, 1);
        logDive(optedOut, 1);
        settle(loggedOut);
        settle(optedOut);

        assertThat(due(loggedOut)).isEmpty();
        assertThat(due(optedOut)).isEmpty();
    }

    @Test
    void theSnapshotIsTheDashboardHeadline() {
        final var diver = seedDiver(true, true);
        logDive(diver, 1);
        logDive(diver, 2);

        final var snapshot = homeData.syncSnapshot(diver.id());
        final var dashboard = homeData.forUser(diver.id(), diver.name());

        assertThat(snapshot.diveCount()).isEqualTo(dashboard.diveCount()).isEqualTo(2);
        assertThat(snapshot.maxDiveNumber()).isEqualTo(dashboard.maxDiveNumber());
        assertThat(snapshot.maxDepth()).isEqualTo(dashboard.maxDepth());
        assertThat(snapshot.lastDiveStart()).isEqualTo(dashboard.lastDiveStart());
        assertThat(snapshot.recentDives()).isEqualTo(dashboard.recentDives());
    }
}
