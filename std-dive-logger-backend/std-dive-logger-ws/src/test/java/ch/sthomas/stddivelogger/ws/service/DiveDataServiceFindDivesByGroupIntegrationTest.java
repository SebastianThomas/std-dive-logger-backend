package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.GroupRepository;
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
import ch.sthomas.stddivelogger.model.entity.GroupEntity;
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
 * Regression coverage for the {@code DiveService.getDivesByGroup} sort fix: a group's dive feed
 * used to sort by {@code DiveSortColumn.ID} (insertion order), not the dive's own date - two dives
 * inserted out of chronological order (e.g. importing an older dive after a newer one) would show
 * up in the wrong order in the group's shared feed. Fixed via a dedicated {@code
 * DiveSortColumn.DATE} backed by {@code t_dive_summary.dive_start}, since {@code
 * findByGroupPrivilege}'s native query can't reach a joined table's column through Spring Data's
 * generic {@code Pageable}-sort mechanism (see {@code
 * DiveRepository.findByGroupPrivilegeOrderByDiveStart}'s own doc comment).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveDataServiceFindDivesByGroupIntegrationTest {

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
    @Autowired private GroupRepository groupRepository;

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
        final var dive =
                new DiveEntity(
                        number,
                        "find-by-group-it-dive-" + number,
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
        userEntity = userRepository.save(new UserEntity("find-by-group-it@test.ch", "hash", "IT"));
        user = userEntity.toRecord();
        suit = suitRepository.save(new SuitEntity(userEntity, Suit.createUnknown(user)));
        site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Find By Group IT Site", new Location(47.0, 8.0).toPoint()));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer FindByGroup"));
        computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "FIND-BY-GROUP-IT-COMPUTER", manufacturer, userEntity));
    }

    @Test
    void findDivesByGroupSortsChronologicallyNotByInsertionOrder() {
        // Inserted with the *later* dive first, so its DB id ends up lower - if this still
        // silently sorted by DiveSortColumn.ID (the pre-fix default), it would come back first
        // despite being the more recent dive.
        final var laterDive = createDive(2, Instant.parse("2026-06-20T09:00:00Z"));
        final var earlierDive = createDive(1, Instant.parse("2026-06-05T09:00:00Z"));

        final var group =
                groupRepository.save(new GroupEntity("find-by-group-it-group", userEntity));
        diveDataService.saveGroupReader(laterDive.getId(), group.toRecord().id());
        diveDataService.saveGroupReader(earlierDive.getId(), group.toRecord().id());

        final var result =
                diveDataService.findDivesByGroup(
                        group.toRecord().id(),
                        0,
                        20,
                        DiveSort.ofNullable(DiveSortColumn.DATE, SortDirection.ASCENDING));

        assertThat(result.result()).hasSize(2);
        assertThat(result.result().get(0).number()).isEqualTo(1);
        assertThat(result.result().get(1).number()).isEqualTo(2);
    }
}
