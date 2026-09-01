package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.dive.DiveFilterParams;
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
 * Regression coverage for {@code DiveFilterParams.minNumber}/{@code maxNumber} - added so the
 * frontend's trip/course editor can offer "add my dives 120-126" as a single bulk action instead of
 * one search-and-click per dive, reusing the existing {@code /v1/dives/filtered} endpoint rather
 * than a new one.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveDataServiceFindFilteredByNumberRangeIntegrationTest {

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
                        "find-by-number-range-it-dive-" + number,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
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
                userRepository.save(
                        new UserEntity("find-by-number-range-it@test.ch", "hash", "IT"));
        user = userEntity.toRecord();
        suit = suitRepository.save(new SuitEntity(userEntity, Suit.createUnknown(user)));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Find By Number Range IT Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer FindByNumberRange"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null,
                                "FIND-BY-NUMBER-RANGE-IT-COMPUTER",
                                manufacturer,
                                userEntity));
    }

    @Test
    void selectsOnlyDivesWithNumbersInsideTheInclusiveRange() {
        for (var n = 118; n <= 128; n++) {
            createDive(n, Instant.parse("2026-01-01T09:00:00Z").plusSeconds(n * 3600L));
        }

        final var result =
                diveDataService.findFiltered(
                        user.id(),
                        new DiveFilterParams(
                                null, null, null, null, null, null, null, null, null, null, 120,
                                126, null),
                        DiveSort.ofNullable(DiveSortColumn.NUMBER, SortDirection.ASCENDING),
                        0,
                        20);

        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.result())
                .extracting("number")
                .containsExactly(120, 121, 122, 123, 124, 125, 126);
    }

    @Test
    void highlightedTrueKeepsOnlyStarredDives() {
        final var d1 = createDive(1, Instant.parse("2026-01-01T09:00:00Z"));
        createDive(2, Instant.parse("2026-01-02T09:00:00Z"));
        final var d3 = createDive(3, Instant.parse("2026-01-03T09:00:00Z"));
        d1.setHighlighted(true);
        d3.setHighlighted(true);
        diveRepository.saveAllAndFlush(List.of(d1, d3));

        final var filtered =
                diveDataService.findFiltered(
                        user.id(),
                        new DiveFilterParams(
                                null, null, null, null, null, null, null, null, null, null, null,
                                null, true),
                        DiveSort.ofNullable(DiveSortColumn.NUMBER, SortDirection.ASCENDING),
                        0,
                        20);
        assertThat(filtered.result()).extracting("number").containsExactly(1, 3);

        // null highlighted -> no filtering
        final var all =
                diveDataService.findFiltered(
                        user.id(),
                        new DiveFilterParams(
                                null, null, null, null, null, null, null, null, null, null, null,
                                null, null),
                        DiveSort.ofNullable(DiveSortColumn.NUMBER, SortDirection.ASCENDING),
                        0,
                        20);
        assertThat(all.result()).hasSize(3);
    }

    @Test
    void aSingleOpenEndedBoundStillWorks() {
        for (var n = 1; n <= 5; n++) {
            createDive(n, Instant.parse("2026-01-01T09:00:00Z").plusSeconds(n * 3600L));
        }

        final var result =
                diveDataService.findFiltered(
                        user.id(),
                        new DiveFilterParams(
                                null, null, null, null, null, null, null, null, null, null, 4, null,
                                null),
                        DiveSort.ofNullable(DiveSortColumn.NUMBER, SortDirection.ASCENDING),
                        0,
                        20);

        assertThat(result.result()).extracting("number").containsExactly(4, 5);
    }
}
