package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;

import org.apache.commons.lang3.tuple.Pair;
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

import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Regression coverage for {@code DiveService.createEmptyDive} - manual dive entry used to save with
 * zero profiles, which {@code DiveSummaryEntity.update()} then crashed on ({@code
 * profiles.getFirst()}/{@code getLast()} on an empty list). Fixed by building a synthetic 3-point
 * profile (surface/max-depth/surface) from the required {@code maxDepth}/{@code duration} fields.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveServiceCreateEmptyDiveIntegrationTest {

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

    @Test
    void createEmptyDiveSavesASyntheticThreePointProfile() throws Exception {
        final var userEntity =
                userRepository.save(new UserEntity("manual-dive-it@test.ch", "hash", "IT"));
        final var user = userEntity.toRecord();
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Manual Dive IT Site", new Location(47.0, 8.0).toPoint()));

        final var body =
                new UploadDiveBody(
                        null,
                        "manual-dive-it",
                        site.toRecord().id(),
                        27.5,
                        Duration.ofMinutes(40),
                        Instant.parse("2026-06-01T09:00:00Z"));

        final var savedDive = diveService.createEmptyDive(user, body);
        final var dive = diveService.getDiveById(user, savedDive.id()).orElseThrow();

        assertThat(dive.profiles()).hasSize(1);
        final var profile = dive.profiles().getFirst();
        final var measurements = Objects.requireNonNull(profile.measurements());
        assertThat(measurements).hasSize(3);
        assertThat(measurements.stream().mapToDouble(m -> m.measurement().depth()).max())
                .hasValue(27.5);
        assertThat(Duration.between(profile.start(), profile.end()))
                .isEqualTo(Duration.ofMinutes(40));
        // The synthetic surface/max-depth/surface profile isn't a real depth-time curve -
        // averaging it (e.g. to maxDepth/3) would be a fabricated number, not a real average.
        assertThat(dive.summary().averageDepth()).isNull();
        assertThat(dive.manualEntry()).isTrue();
        // A manual dive never exposes a preview image - a graph of the synthetic profile is a
        // meaningless triangle. An explicit regenerate request is a no-op (returns the dive
        // unchanged, still with no preview) rather than generating one.
        assertThat(dive.previewImage()).isNull();
        final Dive regenerated =
                Objects.requireNonNull(diveService.createSaveDivePreview(user, savedDive.id()));
        assertThat(regenerated.previewImage()).isNull();
        assertThat(diveService.getDiveById(user, savedDive.id()).orElseThrow().previewImage())
                .isNull();

        try (final var writer = new StringWriter()) {
            GraphImageCreator.fromDive(
                    dive,
                    writer,
                    Map.of(
                            DiveMeasurement.DiveMeasurementProperty.DEPTH,
                            Pair.of(DiveMeasurement::depth, LegendType.LEFT)),
                    new java.awt.Dimension(800, 450));
            assertThat(writer.toString()).isNotBlank();
        }
    }

    @Test
    void twoManualDivesAtTheSameStartAreRejectedWithAClearMessageNotA500() {
        final var user =
                userRepository
                        .save(new UserEntity("manual-dive-collision-it@test.ch", "hash", "IT"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Manual Dive Collision IT Site",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord();
        final var start = Instant.parse("2026-06-01T09:00:00Z");

        diveService.createEmptyDive(
                user,
                new UploadDiveBody(
                        null, "collision-1", site.id(), 20.0, Duration.ofMinutes(30), start));

        // Same synthetic "Manual" computer + identical start -> would violate t_dive_profiles'
        // (fk_dive_computer, dive_profile_start) unique constraint. Must surface as a plain 400,
        // not the post-commit UnexpectedRollbackException it used to.
        assertThatThrownBy(
                        () ->
                                diveService.createEmptyDive(
                                        user,
                                        new UploadDiveBody(
                                                null,
                                                "collision-2",
                                                site.id(),
                                                18.0,
                                                Duration.ofMinutes(25),
                                                start)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact date and time");

        // A different start on the same day is fine.
        final var ok =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "collision-3",
                                site.id(),
                                18.0,
                                Duration.ofMinutes(25),
                                start.plusSeconds(60)));
        assertThat(ok.id()).isPositive();
    }

    @Test
    void diverCanExplicitlySetAManualDivesAverageDepth() {
        final var userEntity =
                userRepository.save(
                        new UserEntity("manual-dive-avgdepth-it@test.ch", "hash", "IT"));
        final var user = userEntity.toRecord();
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Manual Dive Avg Depth IT Site",
                                new Location(47.0, 8.0).toPoint()));

        final var savedDive =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "manual-dive-avgdepth-it",
                                site.toRecord().id(),
                                20.0,
                                Duration.ofMinutes(30),
                                Instant.parse("2026-06-01T09:00:00Z")));

        final var updated =
                diveService.updateDive(
                        user,
                        new UpdateDiveBody(
                                savedDive.id(),
                                savedDive.number(),
                                null,
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
                                14.2));

        assertThat(updated.summary().averageDepth()).isEqualTo(14.2);

        // A later, unrelated edit (no averageDepth in this update) must not wipe the explicitly-set
        // value back to null.
        final var afterUnrelatedEdit =
                diveService.updateDive(
                        user,
                        new UpdateDiveBody(
                                savedDive.id(),
                                savedDive.number(),
                                "Some notes",
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

        assertThat(afterUnrelatedEdit.summary().averageDepth()).isEqualTo(14.2);
    }

    @Test
    void setManualDiveStartTimeShiftsTheSyntheticProfileAndSummary() {
        final var user =
                userRepository
                        .save(new UserEntity("manual-dive-redate-it@test.ch", "hash", "IT"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Manual Dive Redate IT Site",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord();
        final var originalStart = Instant.parse("2026-06-01T09:00:00Z");
        final var savedDive =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "manual-dive-redate-it",
                                site.id(),
                                20.0,
                                Duration.ofMinutes(30),
                                originalStart));
        assertThat(savedDive.summary().start()).isEqualTo(originalStart);

        final var newStart = Instant.parse("2025-12-24T14:30:00Z");
        final var redated = diveService.setManualDiveStartTime(user, savedDive.id(), newStart);

        assertThat(redated.summary().start()).isEqualTo(newStart);
        assertThat(redated.summary().end()).isEqualTo(newStart.plus(Duration.ofMinutes(30)));
        assertThat(redated.profiles().getFirst().start()).isEqualTo(newStart);
        assertThat(Objects.requireNonNull(redated.profiles().getFirst().measurements()).getFirst())
                .satisfies(m -> assertThat(m.measurement().time()).isEqualTo(newStart));
        // Max depth (the one real datum) survives the shift.
        assertThat(redated.summary().maxDepth()).isEqualTo(20.0);
    }

    @Test
    void setManualDiveStartTimeRejectsACollisionWithAnotherManualDive() {
        final var user =
                userRepository
                        .save(
                                new UserEntity(
                                        "manual-dive-redate-collision-it@test.ch", "hash", "IT"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Manual Dive Redate Collision IT Site",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord();
        final var first =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "redate-collision-1",
                                site.id(),
                                20.0,
                                Duration.ofMinutes(30),
                                Instant.parse("2026-06-01T09:00:00Z")));
        final var second =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "redate-collision-2",
                                site.id(),
                                18.0,
                                Duration.ofMinutes(25),
                                Instant.parse("2026-06-01T11:00:00Z")));

        assertThatThrownBy(
                        () ->
                                diveService.setManualDiveStartTime(
                                        user, second.id(), first.summary().start()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact date and time");
    }
}
