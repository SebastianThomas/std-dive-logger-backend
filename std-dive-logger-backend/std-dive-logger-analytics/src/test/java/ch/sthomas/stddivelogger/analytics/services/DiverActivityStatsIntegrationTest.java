package ch.sthomas.stddivelogger.analytics.services;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiverActivityStatsDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
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

import jakarta.persistence.EntityManager;

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
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * End-to-end for the cached home activity/trend stats: seeds a logbook with a real two-year gap and
 * asserts the era rate excludes it, the streak / seasonality / cadence maths, the "overdue" nudge,
 * and that {@code findDiverIdsNeedingRecompute} only returns divers whose dives changed.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiverActivityStatsIntegrationTest {

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

    @Autowired private EntityManager entityManager;
    @Autowired private DiverActivityStatsDataService statsService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity user;
    private DiveComputerEntity computer;
    private DiveSiteEntity siteA;
    private DiveSiteEntity siteB;
    private int nextNumber = 1;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserEntity("activity-it@test.ch", "hash", "ActivityIT"));
        final var man =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Activity IT Man"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(null, "ACTIVITY-IT-COMPUTER", man, user));
        siteA =
                diveSiteRepository.save(
                        new DiveSiteEntity("Activity IT A", new Location(47.0, 8.0).toPoint()));
        siteB =
                diveSiteRepository.save(
                        new DiveSiteEntity("Activity IT B", new Location(48.0, 9.0).toPoint()));
    }

    private void dive(final Instant start, final double maxDepth, final DiveSiteEntity site) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, maxDepth, null, List.of(), null, null, null, null,
                                null, null, null, null),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(2400),
                                null,
                                maxDepth,
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
                        null);
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(2400), List.of(m0, m1));
        final var suit = new SuitEntity(user, Suit.createUnknown(user.toRecord()));
        diveRepository.save(
                new DiveEntity(
                        nextNumber++,
                        "activity-it",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(user.toRecord()),
                        user,
                        site,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders");
                        }));
    }

    private static Instant daysAgo(final long d) {
        return Instant.now().minus(d, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void computesAPauseAwareRateStreaksSeasonalityAndAnOverdueNudge() {
        // 3 dives ~4 years ago, a long gap, then one dive every 14 days for the last ~13 months,
        // with the most recent one ~52 days ago (well past this diver's 2-week cadence).
        dive(daysAgo(1500), 12.0, siteA);
        dive(daysAgo(1498), 12.0, siteA);
        dive(daysAgo(1470), 12.0, siteA);
        for (long d = 430; d >= 45; d -= 14) {
            dive(daysAgo(d), 25.0, d < 200 ? siteB : siteA); // deeper + a new site recently
        }

        final var stats = statsService.computeAndStore(user.getId());

        // era excludes the 4-year-old cluster: ~28 dives over ~13 months -> well above the
        // all-time rate of ~31 dives / 50 months.
        assertThat(stats.recentDivesPerYear()).isGreaterThan(18);
        assertThat(stats.eraPrecededByPause()).isTrue();
        assertThat(stats.eraStartMonth()).isNotNull();

        // cadence 14 days; last dive ~52 days ago -> overdue, expected-next date in the past
        assertThat(stats.typicalIntervalDays()).isEqualTo(14);
        assertThat(stats.daysSinceLastDive()).isBetween(50, 54);
        assertThat(stats.overdue()).isTrue();
        assertThat(stats.expectedNextDiveBy()).isBefore(Instant.now());

        // streak: a run of consecutive months in the recent era, but not "current" (~52d gap)
        assertThat(stats.longestMonthStreak()).isGreaterThanOrEqualTo(6);
        assertThat(stats.currentMonthStreak()).isZero();

        assertThat(stats.distinctSites()).isEqualTo(2);
        assertThat(stats.busiestMonth()).isBetween(1, 12);
        assertThat(stats.divesByMonth()).isNotEmpty();
    }

    @Test
    void onlyReturnsDiversWhoseDivesChangedSinceTheLastComputation() {
        dive(daysAgo(10), 15.0, siteA);
        final var other =
                userRepository.save(new UserEntity("activity-it-2@test.ch", "h", "ActivityIT2"));

        entityManager.flush();
        // both start out needing a compute
        assertThat(statsService.findDiverIdsNeedingRecompute(50)).contains(user.getId());

        statsService.computeAndStore(user.getId());
        entityManager.flush();
        // now that user is cached and unchanged -> not a candidate
        assertThat(statsService.findDiverIdsNeedingRecompute(50)).doesNotContain(user.getId());

        // a new dive changes the fingerprint -> back on the list
        dive(daysAgo(2), 15.0, siteA);
        entityManager.flush();
        assertThat(statsService.findDiverIdsNeedingRecompute(50)).contains(user.getId());

        assertThat(other.getId()).isNotNull(); // (other never had dives, never a candidate)
        assertThat(statsService.findDiverIdsNeedingRecompute(50)).doesNotContain(other.getId());
    }
}
