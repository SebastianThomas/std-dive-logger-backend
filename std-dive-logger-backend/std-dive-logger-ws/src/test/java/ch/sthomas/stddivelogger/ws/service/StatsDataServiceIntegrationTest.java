package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.TagDefinitionRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.StatsDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.TagDefinitionEntity;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Regression coverage for {@link StatsDataService}'s buddy-count and temperature aggregation across
 * breakdown dimensions (year, dive site, buddy, tag, tag-filter, overall) - each of these now runs
 * as a single grouped SQL query per call instead of one query per group, so this also doubles as
 * correctness coverage for that consolidation (identical results, far fewer round trips).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class StatsDataServiceIntegrationTest {

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

    @Autowired private StatsDataService statsDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private SuitRepository suitRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    @Autowired private TagDefinitionRepository tagDefinitionRepository;

    private UserEntity userEntity;
    private User user;
    private long tagId;

    private DiveEntity createDive(
            final int number,
            final Instant start,
            final String buddyName,
            final double tempA,
            final double tempB,
            final long ttsASeconds,
            final long ttsBSeconds,
            final DiveSiteEntity site,
            final SuitEntity suit,
            final DiveComputerEntity computer,
            final TagDefinitionEntity tag) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start,
                                new Temperature(tempA, Temperature.TemperatureUnit.CELSIUS),
                                10.0,
                                null,
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                java.time.Duration.ofSeconds(ttsASeconds)),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(60),
                                new Temperature(tempB, Temperature.TemperatureUnit.CELSIUS),
                                12.0,
                                null,
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                java.time.Duration.ofSeconds(ttsBSeconds)),
                        null);
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(60), List.of(m0, m1));
        final var dive =
                new DiveEntity(
                        number,
                        "stats-it-dive-" + number,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(userEntity.toRecord()),
                        userEntity,
                        site,
                        List.of(profile),
                        List.of(buddyName),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });
        final var saved = diveRepository.save(dive);
        saved.setManualTags(List.of(tag));
        return diveRepository.save(saved);
    }

    @BeforeEach
    void setUp() {
        userEntity = userRepository.save(new UserEntity("stats-it-test@test.ch", "hash", "IT"));
        user = userEntity.toRecord();
        final var suit =
                suitRepository.save(
                        new SuitEntity(
                                userEntity,
                                ch.sthomas.stddivelogger.model.dive.gear.Suit.createUnknown(user)));
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Stats IT Test Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer"));
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "STATS-IT-TEST-COMPUTER", manufacturer, userEntity));
        final var tag =
                tagDefinitionRepository.save(new TagDefinitionEntity("wreck", userEntity, null));
        tagId = tag.getId();

        createDive(
                1,
                Instant.parse("2025-06-01T10:00:00Z"),
                "Alice",
                10.0,
                15.0,
                300L,
                600L,
                site,
                suit,
                computer,
                tag);
        createDive(
                2,
                Instant.parse("2026-01-15T10:00:00Z"),
                "Bob",
                5.0,
                8.0,
                120L,
                90L,
                site,
                suit,
                computer,
                tag);
    }

    @Test
    void yearBreakdownComputesBuddyCountAndTemperaturePerYear() {
        final var byYear = statsDataService.getStatsByYear(user);

        final var stats2025 = Objects.requireNonNull(byYear.get(2025));
        assertThat(stats2025.nrOfBuddies()).isEqualTo(1L);
        assertThat(Objects.requireNonNull(stats2025.maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(stats2025.minTemp()).celsius()).isEqualTo(10.0);

        final var stats2026 = Objects.requireNonNull(byYear.get(2026));
        assertThat(stats2026.nrOfBuddies()).isEqualTo(1L);
        assertThat(Objects.requireNonNull(stats2026.maxTemp()).celsius()).isEqualTo(8.0);
        assertThat(Objects.requireNonNull(stats2026.minTemp()).celsius()).isEqualTo(5.0);
    }

    @Test
    void yearBreakdownComputesAvgAndMaxTtsPerDivesOwnPeakNotRawSampleValues() {
        final var byYear = statsDataService.getStatsByYear(user);

        // 2025's only dive has measurements at 300s/600s TTS - its own peak (600s) is both the
        // "avg across dives" and "max across dives" for a year with a single dive.
        final var stats2025 = Objects.requireNonNull(byYear.get(2025));
        assertThat(Objects.requireNonNull(stats2025.avgMaxTimeToSurface()).toSeconds())
                .isEqualTo(600L);
        assertThat(Objects.requireNonNull(stats2025.maxMaxTimeToSurface()).toSeconds())
                .isEqualTo(600L);

        // 2026's only dive peaks at 120s (not 90s - the smaller sample doesn't win).
        final var stats2026 = Objects.requireNonNull(byYear.get(2026));
        assertThat(Objects.requireNonNull(stats2026.avgMaxTimeToSurface()).toSeconds())
                .isEqualTo(120L);
        assertThat(Objects.requireNonNull(stats2026.maxMaxTimeToSurface()).toSeconds())
                .isEqualTo(120L);
    }

    @Test
    void diveSiteBreakdownComputesBuddyCountAcrossBothDivesAtTheSameSite() {
        final var bySite = statsDataService.getStatsByDiveSite(user);
        final var siteStats = bySite.values().iterator().next();

        // Both dives share the same site - Alice + Bob = 2 unique buddies there.
        assertThat(siteStats.nrOfBuddies()).isEqualTo(2L);
        assertThat(Objects.requireNonNull(siteStats.maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(siteStats.minTemp()).celsius()).isEqualTo(5.0);
    }

    @Test
    void tagBreakdownComputesBuddyCountAndTemperatureAcrossAllTaggedDives() {
        final var byTag = statsDataService.getStatsByTag(user);
        final var wreckStats =
                byTag.stream()
                        .filter(t -> Objects.requireNonNull(t.key()).id() == tagId)
                        .findFirst()
                        .orElseThrow();

        assertThat(wreckStats.stats().nrOfBuddies()).isEqualTo(2L);
        assertThat(Objects.requireNonNull(wreckStats.stats().maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(wreckStats.stats().minTemp()).celsius()).isEqualTo(5.0);
    }

    @Test
    void tagFilterComputesBuddyCountAndTemperatureForMatchingDives() {
        final var filtered =
                Objects.requireNonNull(
                        statsDataService.computeStatsForTagFilter(user, List.of(tagId)));

        assertThat(filtered.nrOfBuddies()).isEqualTo(2L);
        assertThat(Objects.requireNonNull(filtered.maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(filtered.minTemp()).celsius()).isEqualTo(5.0);
    }

    @Test
    void tagFilterWithNoMatchingDivesReturnsAnAllZeroResultRatherThanThrowing() {
        final var otherTag =
                tagDefinitionRepository.save(new TagDefinitionEntity("unused", userEntity, null));

        final var filtered =
                Objects.requireNonNull(
                        statsDataService.computeStatsForTagFilter(user, List.of(otherTag.getId())));

        assertThat(filtered.diveCount()).isZero();
        assertThat(filtered.maxDiveNr()).isEqualTo(-1);
        assertThat(filtered.nrOfBuddies()).isZero();
        assertThat(filtered.maxTemp()).isNull();
        assertThat(filtered.minTemp()).isNull();
    }

    @Test
    void overallStatsAggregateAcrossEveryDiveInOneQuery() {
        final var overall = statsDataService.computeStatsForUser(user);

        assertThat(overall.diveCount()).isEqualTo(2L);
        // Alice + Bob across both dives.
        assertThat(overall.nrOfBuddies()).isEqualTo(2L);
        assertThat(overall.nrOfSites()).isEqualTo(1L);
        assertThat(Objects.requireNonNull(overall.maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(overall.minTemp()).celsius()).isEqualTo(5.0);
        // Dive 1 peaks at 600s TTS, dive 2 at 120s - avg is of those two per-dive peaks (360s),
        // not an average of all four raw sample values (300/600/120/90 -> 277.5s), and max is the
        // larger of the two peaks (600s), not the largest raw sample across all dives (also 600s
        // here, but for a different reason - see the per-year test for a case where the two
        // differ from a raw-sample max).
        assertThat(Objects.requireNonNull(overall.avgMaxTimeToSurface()).toSeconds())
                .isEqualTo(360L);
        assertThat(Objects.requireNonNull(overall.maxMaxTimeToSurface()).toSeconds())
                .isEqualTo(600L);
    }

    @Test
    void buddyBreakdownIsScopedToNamedBuddiesAndReportsPerBuddyTemperature() {
        final var byBuddy = statsDataService.getStatsByBuddy(user);

        final var alice =
                byBuddy.stream()
                        .filter(b -> Objects.requireNonNull(b.key()).equals("Alice"))
                        .findFirst()
                        .orElseThrow();
        assertThat(alice.stats().diveCount()).isEqualTo(1L);
        assertThat(Objects.requireNonNull(alice.stats().maxTemp()).celsius()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(alice.stats().minTemp()).celsius()).isEqualTo(10.0);

        final var bob =
                byBuddy.stream()
                        .filter(b -> Objects.requireNonNull(b.key()).equals("Bob"))
                        .findFirst()
                        .orElseThrow();
        assertThat(bob.stats().diveCount()).isEqualTo(1L);
        assertThat(Objects.requireNonNull(bob.stats().maxTemp()).celsius()).isEqualTo(8.0);
        assertThat(Objects.requireNonNull(bob.stats().minTemp()).celsius()).isEqualTo(5.0);
    }
}
