package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSort;
import ch.sthomas.stddivelogger.model.dive.DiveSortColumn;
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
import ch.sthomas.stddivelogger.model.user.User;

import org.hibernate.query.SortDirection;
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

/**
 * Regression coverage for {@code DiveRepository.findByUser_IdAndComputer}: its original {@code
 * SELECT DISTINCT ... JOIN} form (needed since a dive can have more than one profile from the same
 * computer) threw a real Postgres error - "for SELECT DISTINCT, ORDER BY expressions must appear in
 * select list" - the moment a caller sorted by {@code DiveSortColumn.DATE} (`diveSummary.start`, a
 * joined-table column never in the DISTINCT SELECT list). Never caught before since the default
 * sort is {@code DiveSortColumn.NUMBER}. Rewritten as an {@code EXISTS} subquery instead (never
 * multiplies rows, so no DISTINCT is needed and any sort column works) - this also happened to fix
 * a second, real defect the DISTINCT/JOIN version had: it never actually filtered by the given user
 * id in its own WHERE clause, relying entirely on the caller (DiveService.getDivesByComputer)
 * having already checked computer ownership first.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveDataServiceFindDivesByComputerIntegrationTest {

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

    @Autowired private DiveDataService diveDataService;
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

    private DiveEntity createDive(final int number, final Instant start) {
        final var m0 =
                new DiveMeasurementEntity(
                        new DiveMeasurement(
                                start, null, 10.0, null, List.of(), null, null, null, null, null,
                                null, null, null),
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
                                null,
                                null),
                        null);
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(60), List.of(m0, m1));
        final var dive =
                new DiveEntity(
                        number,
                        "find-by-computer-it-dive-" + number,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
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
                userRepository.save(new UserEntity("find-by-computer-it@test.ch", "hash", "IT"));
        user = userEntity.toRecord();
        suit = suitRepository.save(new SuitEntity(userEntity, Suit.createUnknown(user)));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Find By Computer IT Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer FindByComputer"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "FIND-BY-COMPUTER-IT-COMPUTER", manufacturer, userEntity));
    }

    @Test
    void sortingDivesByComputerByDateNoLongerThrows() {
        // Inserted with the later dive first, so a correct DATE-ascending sort visibly reorders
        // them rather than happening to already match insertion order.
        final var laterDive = createDive(2, Instant.parse("2026-06-20T09:00:00Z"));
        final var earlierDive = createDive(1, Instant.parse("2026-06-05T09:00:00Z"));

        final var result =
                diveDataService.findDivesByUserAndComputer(
                        user,
                        computer.toRecord(),
                        DiveSort.ofNullable(DiveSortColumn.DATE, SortDirection.ASCENDING),
                        0,
                        20);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.result()).hasSize(2);
        assertThat(result.result().get(0).id()).isEqualTo(earlierDive.getId());
        assertThat(result.result().get(1).id()).isEqualTo(laterDive.getId());
    }

    @Test
    void onlyReturnsDivesBelongingToTheGivenUserEvenForTheSameComputer() {
        createDive(1, Instant.parse("2026-06-05T09:00:00Z"));
        final var otherUserEntity =
                userRepository.save(
                        new UserEntity("find-by-computer-it-other@test.ch", "hash", "IT2"));

        final var result =
                diveDataService.findDivesByUserAndComputer(
                        otherUserEntity.toRecord(),
                        computer.toRecord(),
                        DiveSort.ofNullable(DiveSortColumn.NUMBER, SortDirection.ASCENDING),
                        0,
                        20);

        assertThat(result.result()).isEmpty();
    }
}
