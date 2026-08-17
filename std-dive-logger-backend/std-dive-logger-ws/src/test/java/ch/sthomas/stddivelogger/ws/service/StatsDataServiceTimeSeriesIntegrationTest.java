package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.StatsDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.dive.stats.StatsFilters;
import ch.sthomas.stddivelogger.model.dive.stats.StatsGranularity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

/**
 * Regression coverage for a real bug in {@link StatsDataService#getTimeSeries}: {@link
 * DiveGasConsumption#EMPTY} persists {@code rmv_liters = 0} (a primitive {@code double}, not a
 * nullable column) for every dive that never had real gas-consumption data computed - the
 * overwhelming majority of casual OC dives, which don't track cylinder pressure. Averaging that raw
 * column meant every bucket's "average RMV" was pulled toward zero by dives that simply never had
 * an RMV value at all, not by any dive that genuinely breathed less gas. Fixed via {@code
 * NULLIF(gc.rmv_liters, 0)} in the shared {@code filtered_dives} CTE, since an RMV of exactly 0.0
 * l/min isn't a physically meaningful reading for an actual dive either.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class StatsDataServiceTimeSeriesIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
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

    @Autowired private StatsDataService statsDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private SuitRepository suitRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity userEntity;
    private User user;
    private SuitEntity suit;
    private DiveSiteEntity site;
    private DiveComputerEntity computer;

    private DiveEntity createDive(final int number, final Instant start, final double rmvLiters) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, 10.0, null, List.of(), null, null, null, null, null,
                                null, null),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(60),
                                null,
                                12.0,
                                null,
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        null);
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(60), List.of(m0, m1));
        final var gasConsumption =
                rmvLiters > 0 ? new DiveGasConsumption(0, rmvLiters, 0) : DiveGasConsumption.EMPTY;
        final var dive =
                new DiveEntity(
                        number,
                        "stats-timeseries-it-dive-" + number,
                        "",
                        Visibility.EMPTY,
                        gasConsumption,
                        suit,
                        null,
                        DiveConfiguration.createEmpty(user),
                        userEntity,
                        site,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });
        return diveRepository.save(dive);
    }

    @BeforeEach
    void setUp() {
        userEntity =
                userRepository.save(new UserEntity("stats-timeseries-it@test.ch", "hash", "IT"));
        user = userEntity.toRecord();
        suit = suitRepository.save(new SuitEntity(userEntity, Suit.createUnknown(user)));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Stats TimeSeries IT Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer TimeSeries"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "STATS-TIMESERIES-IT-COMPUTER", manufacturer, userEntity));
    }

    @Test
    void averageRmvIgnoresDivesWithNoRealGasConsumptionDataInsteadOfTreatingThemAsZero() {
        // Same month bucket: one dive with a real RMV, one that never had gas consumption
        // tracked (persisted as DiveGasConsumption.EMPTY, i.e. rmv_liters = 0 at the DB level).
        createDive(1, Instant.parse("2026-03-05T10:00:00Z"), 18.0);
        createDive(2, Instant.parse("2026-03-20T10:00:00Z"), 0.0);

        final var series =
                statsDataService.getTimeSeries(
                        user, StatsGranularity.MONTH, StatsFilters.EMPTY, null);

        assertThat(series.points()).hasSize(1);
        final var bucket = series.points().getFirst();
        assertThat(bucket.diveCount()).isEqualTo(2L);
        // Not (18 + 0) / 2 = 9.0 - the dive with no real data must not drag the average down.
        assertThat(bucket.avgRmvLiters()).isEqualTo(18.0);
    }

    @Test
    void averageRmvIsNullWhenNoDiveInTheBucketHasRealGasConsumptionData() {
        createDive(1, Instant.parse("2026-04-05T10:00:00Z"), 0.0);
        createDive(2, Instant.parse("2026-04-20T10:00:00Z"), 0.0);

        final var series =
                statsDataService.getTimeSeries(
                        user, StatsGranularity.MONTH, StatsFilters.EMPTY, null);

        assertThat(series.points()).hasSize(1);
        final var bucket = series.points().getFirst();
        assertThat(bucket.diveCount()).isEqualTo(2L);
        assertThat(bucket.avgRmvLiters()).isNull();
    }
}
