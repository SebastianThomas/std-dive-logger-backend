package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.HomeDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.home.HomeBuddy;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecentDive;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class HomeDataServiceIntegrationTest {

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

    @Autowired private HomeDataService homeDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private SuitRepository suitRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity user;
    private SuitEntity suit;
    private DiveSiteEntity site;
    private DiveComputerEntity computer;
    private int nextNumber = 1;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserEntity("home-it@test.ch", "hash", "Home IT"));
        suit = suitRepository.save(new SuitEntity(user, Suit.createUnknown(user.toRecord())));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity("Home IT Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Home IT Manufacturer"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(null, "HOME-IT-COMPUTER", manufacturer, user));
    }

    private DiveEntity dive(
            final UserEntity owner,
            final Instant start,
            final long durationSeconds,
            final double maxDepth,
            final List<String> buddies) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, maxDepth, null, List.of(), null, null, null, null,
                                null, null, null, null),
                        null);
        final var m1 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start.plusSeconds(durationSeconds),
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
                new DiveProfileEntity(
                        computer, start, start.plusSeconds(durationSeconds), List.of(m0, m1));
        final var d =
                new DiveEntity(
                        nextNumber++,
                        "home-it-dive",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        null,
                        DiveConfiguration.createEmpty(owner.toRecord()),
                        owner,
                        site,
                        List.of(profile),
                        buddies,
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });
        return diveRepository.save(d);
    }

    private static Instant daysAgo(final long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    @Test
    void aggregatesCountsWindowsRecordsAndBuddiesForTheUsersOwnDives() {
        dive(user, daysAgo(400), 40 * 60, 18.0, List.of("Alice")); // previous 12 months
        dive(user, daysAgo(200), 55 * 60, 30.0, List.of("Alice", "Bob")); // last 12mo, deepest
        dive(user, daysAgo(20), 90 * 60, 12.0, List.of("Alice")); // last 30d, longest
        dive(user, daysAgo(5), 35 * 60, 22.0, List.of("Bob")); // last 30d

        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        assertThat(home.userName()).isEqualTo("Home IT");
        assertThat(home.diveCount()).isEqualTo(4);
        assertThat(home.maxDiveNumber()).isEqualTo(4);
        assertThat(home.maxDepth()).isEqualTo(30.0);
        assertThat(home.totalBottomTime()).isEqualTo(Duration.ofMinutes(40 + 55 + 90 + 35));

        assertThat(home.windows().last30Days().diveCount()).isEqualTo(2);
        assertThat(home.windows().last30Days().bottomTime()).isEqualTo(Duration.ofMinutes(90 + 35));
        assertThat(home.windows().last365Days().diveCount()).isEqualTo(3);
        assertThat(home.windows().previous365Days().diveCount()).isEqualTo(1);

        assertThat(Objects.requireNonNull(home.records().deepest()).maxDepth()).isEqualTo(30.0);
        assertThat(Objects.requireNonNull(home.records().longest()).bottomTime())
                .isEqualTo(Duration.ofMinutes(90));

        assertThat(home.topBuddies())
                .extracting(HomeBuddy::name)
                .containsExactly("Alice", "Bob"); // Alice on 3 dives, Bob on 2
        assertThat(home.topBuddies().getFirst().diveCount()).isEqualTo(3);
    }

    @Test
    void recentDivesAreTheFiveNewestByStartDescending() {
        for (int i = 0; i < 7; i++) {
            dive(user, daysAgo(7 - i), 30 * 60, 10.0 + i, List.of());
        }

        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        // dives are numbered 1..7 in seed order; i=6 (daysAgo 1) is the newest -> number 7.
        assertThat(home.recentDives())
                .extracting(HomeRecentDive::number)
                .containsExactly(7, 6, 5, 4, 3);
        assertThat(home.recentDives().getFirst().maxDepth()).isEqualTo(16.0);
    }

    @Test
    void recordTieBreakIsDeterministicOnDiveNumber() {
        dive(user, daysAgo(10), 30 * 60, 25.0, List.of());
        final var later = dive(user, daysAgo(5), 30 * 60, 25.0, List.of());

        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        // identical depth + duration -> the higher dive number wins
        assertThat(Objects.requireNonNull(home.records().deepest()).diveNumber())
                .isEqualTo(later.getNumber());
        assertThat(Objects.requireNonNull(home.records().longest()).diveNumber())
                .isEqualTo(later.getNumber());
    }

    @Test
    void highlightedDivesAreOnlyThisUsersStarredOnesNewestFirst() {
        final var other =
                userRepository.save(new UserEntity("home-it-hl-other@test.ch", "h", "Other"));

        dive(user, daysAgo(30), 30 * 60, 12.0, List.of()); // not highlighted
        final var older = dive(user, daysAgo(20), 30 * 60, 18.0, List.of());
        final var newer = dive(user, daysAgo(4), 30 * 60, 25.0, List.of());
        final var strangersStar = dive(other, daysAgo(1), 30 * 60, 40.0, List.of());

        older.setHighlighted(true);
        newer.setHighlighted(true);
        strangersStar.setHighlighted(true);
        // Flush: Q_HIGHLIGHTED is raw JDBC and won't trigger a Hibernate auto-flush of the UPDATEs.
        diveRepository.saveAllAndFlush(List.of(older, newer, strangersStar));

        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        assertThat(home.highlightedDives())
                .extracting(HomeRecentDive::id)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void emptyLogbookReturnsZeroesAndNullsWithoutThrowing() {
        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        assertThat(home.diveCount()).isZero();
        assertThat(home.maxDiveNumber()).isZero();
        assertThat(home.maxDepth()).isNull();
        assertThat(home.totalBottomTime()).isNull();
        assertThat(home.firstDiveStart()).isNull();
        assertThat(home.lastDiveStart()).isNull();
        assertThat(home.divesThisYear()).isZero();
        assertThat(home.windows().last30Days().diveCount()).isZero();
        assertThat(home.windows().last30Days().bottomTime()).isNull();
        assertThat(home.recentDives()).isEmpty();
        assertThat(home.highlightedDives()).isEmpty();
        assertThat(home.topBuddies()).isEmpty();
        assertThat(home.records().deepest()).isNull();
        assertThat(home.records().longest()).isNull();
    }

    @Test
    void doesNotLeakAnotherUsersDivesOrBuddies() {
        final var other =
                userRepository.save(new UserEntity("home-it-other@test.ch", "h", "Other"));
        dive(other, daysAgo(3), 200 * 60, 99.0, List.of("Stranger"));
        dive(user, daysAgo(3), 30 * 60, 15.0, List.of("Alice"));

        final var home = homeDataService.forUser(user.getId(), user.toRecord().name());

        assertThat(home.diveCount()).isEqualTo(1);
        assertThat(home.maxDepth()).isEqualTo(15.0);
        assertThat(Objects.requireNonNull(home.records().deepest()).maxDepth()).isEqualTo(15.0);
        assertThat(home.topBuddies()).extracting(HomeBuddy::name).containsExactly("Alice");
        assertThat(home.recentDives()).hasSize(1);
    }
}
