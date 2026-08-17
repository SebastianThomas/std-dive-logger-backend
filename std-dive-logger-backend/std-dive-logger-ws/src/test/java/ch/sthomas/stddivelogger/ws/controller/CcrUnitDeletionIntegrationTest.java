package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.data.repository.CcrUnitRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DiveService;

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
import java.util.NoSuchElementException;

/**
 * Covers CCR unit deletion end to end against a real database: the plain delete ({@link
 * DiveDataService#deleteCcrUnitById}) must only unlink a unit from any dive configuration/computer
 * that references it - never delete a dive, profile, or computer - while {@link
 * DiveService#deleteCcrUnitAndAllDives} is the separate, explicitly destructive operation that does
 * delete every dive using the unit.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class CcrUnitDeletionIntegrationTest {

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

    @Autowired private DiveService diveService;
    @Autowired private DiveDataService diveDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private CcrUnitRepository ccrUnitRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private UserEntity userEntity;

    private DiveComputerManufacturerEntity manufacturer;

    @BeforeEach
    void setUp() {
        userEntity = userRepository.save(new UserEntity("ccr-unit-it@test.ch", "hash", "IT Test"));
        manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer CCR IT"));
    }

    private DiveComputerEntity createComputer(final String identifier) {
        return diveComputerRepository.save(
                new DiveComputerEntity(null, identifier, manufacturer, userEntity));
    }

    private long createDiveLinkedToCcrUnit(
            final int number, final DiveComputerEntity computer, final CcrUnitEntity ccrUnit) {
        final var diveSite =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "CCR Unit Deletion Test Site " + number,
                                new Location(47.0, 8.0).toPoint()));
        final var start = Instant.parse("2026-01-01T10:00:00Z");
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
                                10.0,
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
        final var userRecord = userEntity.toRecord();
        final var suit = new SuitEntity(userEntity, Suit.createUnknown(userRecord));
        final var configuration =
                new DiveConfiguration(
                        Suit.createUnknown(userRecord),
                        BaseConfiguration.SIDEMOUNT_CCR,
                        null,
                        null,
                        List.of(),
                        ccrUnit.toRecord());
        final var dive =
                new DiveEntity(
                        number,
                        "ccr-unit-it-dive-" + number,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        ccrUnit,
                        configuration,
                        userEntity,
                        diveSite,
                        List.of(profile),
                        List.of(),
                        cs -> {
                            throw new UnsupportedOperationException("no cylinders in this fixture");
                        });
        return diveRepository.save(dive).getId();
    }

    @Test
    void plainDeleteUnlinksButNeverDeletesTheDiveOrComputer() {
        final var ccrUnit =
                ccrUnitRepository.save(
                        new CcrUnitEntity(
                                userEntity,
                                new CcrUnit(
                                        null,
                                        userEntity.getId(),
                                        "rEvo",
                                        "",
                                        false,
                                        BaseConfiguration.SIDEMOUNT_CCR)));
        final var computer = createComputer("PLAIN-DELETE-COMPUTER");
        computer.setCcrUnit(ccrUnit);
        diveComputerRepository.save(computer);
        final var diveId = createDiveLinkedToCcrUnit(1, computer, ccrUnit);

        diveService.deleteCcrUnit(userEntity.toRecord(), ccrUnit.getId());

        assertThat(ccrUnitRepository.findById(ccrUnit.getId())).isEmpty();

        final var survivingDive = diveRepository.findById(diveId).orElseThrow();
        final var survivingConfiguration =
                java.util.Objects.requireNonNull(survivingDive.getConfiguration());
        assertThat(survivingConfiguration.toRecord().ccrUnit()).isNull();
        // Nothing else about the dive's configuration changed.
        assertThat(survivingConfiguration.toRecord().base())
                .isEqualTo(BaseConfiguration.SIDEMOUNT_CCR);

        final var survivingComputer =
                diveComputerRepository.findById(computer.toRecord().id()).orElseThrow();
        assertThat(survivingComputer.getCcrUnit()).isNull();
    }

    @Test
    void plainDeleteRejectsAUnitBelongingToAnotherUser() {
        final var otherUser =
                userRepository.save(new UserEntity("ccr-unit-it-other@test.ch", "hash", "Other"));
        final var ccrUnit =
                ccrUnitRepository.save(
                        new CcrUnitEntity(
                                otherUser,
                                new CcrUnit(
                                        null,
                                        otherUser.getId(),
                                        "Someone else's rEvo",
                                        "",
                                        false,
                                        BaseConfiguration.SIDEMOUNT_CCR)));

        assertThatThrownBy(() -> diveService.deleteCcrUnit(userEntity.toRecord(), ccrUnit.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(ccrUnitRepository.findById(ccrUnit.getId())).isPresent();
    }

    @Test
    void deleteAndAllDivesDeletesEveryDiveUsingTheUnitThenTheUnitItself() {
        final var ccrUnit =
                ccrUnitRepository.save(
                        new CcrUnitEntity(
                                userEntity,
                                new CcrUnit(
                                        null,
                                        userEntity.getId(),
                                        "rEvo",
                                        "",
                                        false,
                                        BaseConfiguration.SIDEMOUNT_CCR)));
        final var computerA = createComputer("BULK-DELETE-COMPUTER-A");
        final var computerB = createComputer("BULK-DELETE-COMPUTER-B");
        final var diveIdA = createDiveLinkedToCcrUnit(1, computerA, ccrUnit);
        final var diveIdB = createDiveLinkedToCcrUnit(2, computerB, ccrUnit);

        final var deletedCount =
                diveService.deleteCcrUnitAndAllDives(userEntity.toRecord(), ccrUnit.getId());

        assertTrue(deletedCount == 2);
        assertThat(diveRepository.findById(diveIdA)).isEmpty();
        assertThat(diveRepository.findById(diveIdB)).isEmpty();
        assertThat(ccrUnitRepository.findById(ccrUnit.getId())).isEmpty();
    }
}
