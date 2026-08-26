package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.service.DiveService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression coverage for the real "Shearwater" vs "Shearwater Research, Inc" duplicate-computer
 * bug: the native XML/DL7 readers hardcode the short company name, while that same company's UDDF
 * export carries its full name - two exact-match lookups for the identical physical device, two
 * separate DiveComputer rows created for it. Fixed by falling back to a fuzzy manufacturer-name
 * match (still requiring an exact serial number) before ever creating a new computer.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveComputerFuzzyManufacturerMatchIntegrationTest {

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
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new UserEntity("computer-fuzzy-it@test.ch", "hash", "IT"));
    }

    private long saveComputer(final String manufacturerName, final String serial) {
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity(manufacturerName));
        return diveComputerRepository
                .save(new DiveComputerEntity(serial, "Perdix 2", manufacturer, user))
                .toRecord()
                .id();
    }

    @Test
    void findsAnExistingComputerWhenTheNewManufacturerNameContainsTheStoredOne() {
        final var id = saveComputer("Shearwater", "SN-001");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "Shearwater Research, Inc", "SN-001");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void findsAnExistingComputerWhenTheStoredManufacturerNameContainsTheNewOne() {
        final var id = saveComputer("Shearwater Research, Inc", "SN-002");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "Shearwater", "SN-002");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void findsAnExistingComputerForACloseTypoOrCapitalizationDifference() {
        final var id = saveComputer("Shearwater", "SN-003");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "SHEARWATRE", "SN-003");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void doesNotFuzzyMatchAcrossDifferentSerialNumbers() {
        saveComputer("Shearwater", "SN-004");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "Shearwater Research, Inc", "SN-DIFFERENT");

        assertThat(found).isEmpty();
    }

    @Test
    void doesNotFuzzyMatchTwoUnrelatedManufacturerNames() {
        saveComputer("Garmin", "SN-005");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "Shearwater", "SN-005");

        assertThat(found).isEmpty();
    }

    @Test
    void prefersTheExactManufacturerMatchOverAFuzzyOne() {
        final var exactId = saveComputer("Shearwater Research, Inc", "SN-006");

        final var found =
                diveDataService.findDiveComputerByUserAndSerialNumber(
                        user.getId(), "Shearwater Research, Inc", "SN-006");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().id()).isEqualTo(exactId);
    }

    @Test
    void getOrCreateDiveComputerReusesTheFuzzyMatchInsteadOfCreatingADuplicate() {
        final var existingId = saveComputer("Shearwater", "SN-007");

        final var result =
                diveService.getOrCreateDiveComputer(
                        user.toRecord(), "Shearwater Research, Inc", "SN-007", "Perdix 2");

        assertThat(result.id()).isEqualTo(existingId);
        assertThat(
                        diveComputerRepository
                                .findByUser_Id(user.getId(), PageRequest.of(0, 10))
                                .getTotalElements())
                .isEqualTo(1);
    }
}
