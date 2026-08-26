package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.conditions.Current;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

/**
 * Coverage for the "backfill" guide: the queue of dives still missing at least one checklist item
 * (visibility, gas consumption, water type, leader, notes), ordered most-incomplete/oldest first.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveBackfillIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("ch.sthomas.stddivelogger.ws.jwt-secret", () -> "test-jwt-secret");
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret", () -> "test-jwt-refresh-secret");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.bucket", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.account-id", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.access-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.secret-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private long createDive(
            final User user, final long siteId, final int number, final Instant start) {
        return diveService
                .createEmptyDive(
                        user,
                        new UploadDiveBody(
                                number,
                                "backfill-it-" + number,
                                siteId,
                                10.0,
                                Duration.ofMinutes(10),
                                start))
                .id();
    }

    @Test
    void queuesIncompleteDivesMostMissingAndOldestFirst() {
        final var user =
                userRepository.save(new UserEntity("backfill-it@test.ch", "hash", "A")).toRecord();
        final var siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Backfill IT Site", new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();

        final var completeId = createDive(user, siteId, 1, Instant.parse("2026-01-01T09:00:00Z"));
        final var partialId = createDive(user, siteId, 2, Instant.parse("2026-02-01T09:00:00Z"));
        final var emptyId = createDive(user, siteId, 3, Instant.parse("2026-03-01T09:00:00Z"));

        // Fully backfilled - fills in every checklist item in one go (conditions/waterType are
        // always applied unconditionally by DiveDataService.updateDive, so a second partial call
        // here would silently wipe them back to null).
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        completeId,
                        1,
                        "Great dive",
                        0,
                        null,
                        new DiveGasConsumption(18.0, 12.0, 1800.0),
                        new Visibility(15.0, "Clear", VisibilityFeeling.HIGH),
                        null,
                        null,
                        null,
                        WaterType.SALT,
                        new Current(0.5, "Mild", 1),
                        null,
                        null,
                        true,
                        null,
                        null));

        // Only notes filled in - still missing visibility/gas/waterType/leader.
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        partialId,
                        2,
                        "Partial notes",
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null));

        final var queue = diveService.getBackfillQueue(user);

        assertThat(queue).extracting("diveId").containsExactly(emptyId, partialId);
        assertThat(queue.get(0).missingCount()).isEqualTo(5);
        assertThat(queue.get(1).missingCount()).isEqualTo(4);
        assertThat(queue.get(1).missingFields()).doesNotContain("NOTES");
    }
}
