package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.SuitRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
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
import ch.sthomas.stddivelogger.service.DiveService;

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
import java.util.List;
import java.util.Objects;

/**
 * Regression test for a real production bug: editing a dive's configuration (e.g. changing suit and
 * cylinders from the edit-dive form) threw {@code JpaSystemException: A collection with orphan
 * deletion was no longer referenced by the owning entity instance:
 * DiveConfigurationEntity.cylinders}. Root cause was {@code DiveConfigurationEntity.update()}
 * replacing the {@code cylinders} field with a brand-new {@code List} instance instead of mutating
 * the existing Hibernate-managed collection in place, which breaks {@code orphanRemoval} tracking.
 * A pure-Mockito unit test can't catch this - it only surfaces on a real Hibernate flush against a
 * real persistence context, hence the full {@code @SpringBootTest} + Testcontainers Postgres setup.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveConfigurationUpdateIntegrationTest {

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
    @Autowired private SuitRepository suitRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveRepository diveRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;
    @Autowired private EntityManager entityManager;

    private UserEntity userEntity;
    private long suitId;
    private long diveId;

    @BeforeEach
    void setUp() {
        userEntity = userRepository.save(new UserEntity("config-it-test@test.ch", "hash", "IT"));
        final var suit =
                suitRepository.save(
                        new SuitEntity(userEntity, Suit.createUnknown(userEntity.toRecord())));
        suitId = Objects.requireNonNull(suit.toRecord().id());
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Config IT Test Site", new Location(47.0, 8.0).toPoint()));

        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer"));
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(
                                null, "CONFIG-IT-TEST-COMPUTER", manufacturer, userEntity));
        final var start = Instant.parse("2026-01-01T10:00:00Z");
        final var measurements =
                List.of(
                        new DiveMeasurementEntity(
                                new DiveMeasurement(
                                        start, null, 10.0, null, List.of(), null, null, null, null,
                                        null, null, null),
                                null),
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
                                null));
        final var profile =
                new DiveProfileEntity(computer, start, start.plusSeconds(60), measurements);

        final var dive =
                new DiveEntity(
                        1,
                        "config-it-test-dive",
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        suit,
                        null,
                        DiveConfiguration.createEmpty(userEntity.toRecord()),
                        userEntity,
                        site,
                        List.of(profile),
                        List.of(),
                        // Never actually invoked - this dive's initial configuration has no
                        // cylinders (updateDive() below resolves cylinder sizes via the real
                        // DiveDataService.toEntity(CylinderSize) instead).
                        cs -> {
                            throw new UnsupportedOperationException(
                                    "no cylinders in this test fixture");
                        });
        diveId = diveRepository.save(dive).getId();
    }

    private static DiveConfigurationCylinder cylinder(final double liters, final String notes) {
        return new DiveConfigurationCylinder(
                0,
                new CylinderSize(CylinderSizeUnit.LITER, liters),
                200.0,
                50.0,
                notes,
                Gas.AIR,
                CylinderRole.OC,
                null,
                null);
    }

    @Test
    void updatingAnExistingConfigurationsCylindersDoesNotThrow() {
        final var body =
                new UpdateDiveBody(
                        diveId,
                        1,
                        null,
                        suitId,
                        new DiveConfiguration(
                                Suit.createUnknown(userEntity.toRecord()),
                                BaseConfiguration.SINGLE_TANK,
                                null,
                                null,
                                List.of(cylinder(11.1, "back gas")),
                                null),
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
                        null);

        assertThatCode(() -> diveService.updateDive(userEntity.toRecord(), body))
                .doesNotThrowAnyException();

        final var reloaded = diveService.getDiveById(userEntity.toRecord(), diveId).orElseThrow();
        assertThat(Objects.requireNonNull(reloaded.configuration()).cylinders()).hasSize(1);
        assertThat(Objects.requireNonNull(reloaded.configuration()).cylinders().getFirst().notes())
                .isEqualTo("back gas");
    }

    @Test
    void replacingAnAlreadyPersistedCylinderSetDoesNotThrow() {
        final var firstUpdate =
                new UpdateDiveBody(
                        diveId,
                        1,
                        null,
                        suitId,
                        new DiveConfiguration(
                                Suit.createUnknown(userEntity.toRecord()),
                                BaseConfiguration.SIDEMOUNT,
                                null,
                                null,
                                List.of(cylinder(11.1, "left"), cylinder(11.1, "right")),
                                null),
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
                        null);
        diveService.updateDive(userEntity.toRecord(), firstUpdate);
        // Flush + clear the persistence context so the second update reloads the dive fresh from
        // the DB, the same as it would across two separate HTTP requests in production (each its
        // own transaction) rather than reusing the still-managed, not-yet-flushed collection from
        // the first update within this single test transaction.
        entityManager.flush();
        entityManager.clear();

        // The exact reported scenario: editing an already-persisted, non-empty cylinder set - this
        // is what triggered "A collection with orphan deletion was no longer referenced by the
        // owning entity instance" before the fix.
        final var secondUpdate =
                new UpdateDiveBody(
                        diveId,
                        1,
                        null,
                        suitId,
                        new DiveConfiguration(
                                Suit.createUnknown(userEntity.toRecord()),
                                BaseConfiguration.SINGLE_TANK,
                                null,
                                null,
                                List.of(cylinder(15.0, "single back gas")),
                                null),
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
                        null);

        assertThatCode(() -> diveService.updateDive(userEntity.toRecord(), secondUpdate))
                .doesNotThrowAnyException();

        final var reloaded = diveService.getDiveById(userEntity.toRecord(), diveId).orElseThrow();
        assertThat(Objects.requireNonNull(reloaded.configuration()).cylinders()).hasSize(1);
        assertThat(Objects.requireNonNull(reloaded.configuration()).cylinders().getFirst().notes())
                .isEqualTo("single back gas");
    }
}
