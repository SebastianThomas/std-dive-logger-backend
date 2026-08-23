package ch.sthomas.stddivelogger.analytics.services;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;

import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A real end-to-end test of the analytics recompute pipeline against a throwaway Testcontainers
 * Postgres instance: boots the app for real (so Flyway actually runs every migration, confirming
 * they apply cleanly against a real database), then drives {@link
 * AnalyticsService#computeAnalytics()} against a hand-built dive to confirm the job-state-driven
 * recompute logic (new dive gets computed, unchanged dive is skipped on the next run, a version
 * bump flags it again) and that the exact data-service methods behind the `/segments` and `/rates`
 * HTTP endpoints return sane results afterward.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class AnalyticsRecomputeIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired private AnalyticsService analyticsService;
    @Autowired private AnalyticsDataService analyticsDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    @Autowired private DiveRepository diveRepository;

    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userEntity = userRepository.save(new UserEntity("it-test@test.ch", "hash", "IT Test"));
    }

    /** A steady descent to 20m (0-60s), then a hold at 20m (60-130s), one sample per second. */
    private long createDiveWithDescentAndHoldProfile() {
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer"));
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(null, "IT-TEST-COMPUTER", manufacturer, userEntity));
        final var diveSite =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Integration Test Site", new Location(47.0, 8.0).toPoint()));

        final var start = Instant.parse("2026-01-01T10:00:00Z");
        final var measurements = new ArrayList<DiveMeasurementEntity>();
        for (var t = 0; t <= 130; t++) {
            final var depth = t <= 60 ? t * (20.0 / 60.0) : 20.0;
            measurements.add(
                    new DiveMeasurementEntity(
                            new DiveMeasurement(
                                    start.plusSeconds(t),
                                    null,
                                    depth,
                                    null,
                                    List.of(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null),
                            null));
        }
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(130), measurements);

        final var userRecord = userEntity.toRecord();
        final var suit = new SuitEntity(userEntity, Suit.createUnknown(userRecord));
        final var configuration = DiveConfiguration.createEmpty(userRecord);

        final var dive =
                new DiveEntity(
                        1,
                        "it-test-dive",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        configuration,
                        userEntity,
                        diveSite,
                        List.of(profile),
                        List.of(),
                        // Never actually invoked - this fixture's configuration has no cylinders.
                        cs -> {
                            throw new UnsupportedOperationException(
                                    "no cylinders in this test fixture");
                        });
        return diveRepository.save(dive).getId();
    }

    @Test
    void computesSegmentsAndRecordsJobStateForANewDive() {
        final var diveId = createDiveWithDescentAndHoldProfile();

        final var result = analyticsService.computeAnalytics();
        assertThat(result.successful()).isTrue();

        final var segments =
                analyticsDataService.findSegmentsByDiveId(userEntity.toRecord(), diveId, false);
        assertThat(segments).isNotEmpty();
        final var types =
                segments.stream().map(s -> s.segment().type()).collect(Collectors.toSet());
        assertThat(types)
                .containsAnyOf(DiveProfileSegmentType.DESCENT, DiveProfileSegmentType.HOLD_LEVEL);

        // Exercises the exact data-service method behind the /rates HTTP endpoint too.
        final var jobState = analyticsDataService.findRatesByDiveId(userEntity.toRecord(), diveId);
        assertThat(jobState).hasSize(1);
        assertThat(jobState.getFirst().rates()).isNotEmpty();
        // Positive rate (descending) should show up during the first 60s of the profile.
        assertThat(jobState.getFirst().rates().stream().anyMatch(r -> r.rateMetersPerMinute() > 5))
                .isTrue();
    }

    @Test
    void secondRunDoesNotRecomputeAnUnchangedDive() {
        final var diveId = createDiveWithDescentAndHoldProfile();

        analyticsService.computeAnalytics();
        final var segmentsAfterFirstRun =
                analyticsDataService.findSegmentsByDiveId(userEntity.toRecord(), diveId, false);
        final var idsAfterFirstRun =
                segmentsAfterFirstRun.stream()
                        .map(DiveProfileSegmentWithId::id)
                        .collect(Collectors.toSet());

        analyticsService.computeAnalytics();
        final var segmentsAfterSecondRun =
                analyticsDataService.findSegmentsByDiveId(userEntity.toRecord(), diveId, false);
        final var idsAfterSecondRun =
                segmentsAfterSecondRun.stream()
                        .map(DiveProfileSegmentWithId::id)
                        .collect(Collectors.toSet());

        // Same segment rows (same ids) - proves the dive was NOT deleted-and-recomputed again.
        assertThat(idsAfterSecondRun).isEqualTo(idsAfterFirstRun);
    }

    @Test
    void aVersionBumpFlagsAnAlreadyComputedDiveForRecompute() {
        final var diveId = createDiveWithDescentAndHoldProfile();
        analyticsService.computeAnalytics();

        final var candidatesAtCurrentVersion =
                analyticsDataService.findDivesNeedingRecompute(
                        AnalyticsService.JOB_MODULE,
                        AnalyticsService.JOB_NAME,
                        AnalyticsService.ANALYTICS_VERSION,
                        100);
        assertThat(idsOf(candidatesAtCurrentVersion.dives())).doesNotContain(diveId);

        final var candidatesAtNextVersion =
                analyticsDataService.findDivesNeedingRecompute(
                        AnalyticsService.JOB_MODULE,
                        AnalyticsService.JOB_NAME,
                        AnalyticsService.ANALYTICS_VERSION + 1,
                        100);
        assertThat(idsOf(candidatesAtNextVersion.dives())).contains(diveId);
    }

    private static Set<Long> idsOf(final List<Dive> dives) {
        return dives.stream().map(Dive::id).collect(Collectors.toSet());
    }
}
