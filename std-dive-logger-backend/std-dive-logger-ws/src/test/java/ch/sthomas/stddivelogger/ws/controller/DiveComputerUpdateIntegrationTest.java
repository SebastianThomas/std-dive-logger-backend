package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveComputerManufacturerRepository;
import ch.sthomas.stddivelogger.data.repository.DiveComputerRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.entity.DiveComputerEntity;
import ch.sthomas.stddivelogger.model.entity.DiveComputerManufacturerEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;

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

/**
 * Regression test for an IDOR: {@code DiveDataService.updateDiveComputer} previously looked up the
 * target computer with a plain {@code findById}, so any authenticated user could rename or re-link
 * (to their own CCR unit) a dive computer owned by someone else, simply by guessing/ enumerating
 * computer ids. Fixed by scoping the lookup with {@code findByIdAndUser_Id}.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveComputerUpdateIntegrationTest {

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
    @Autowired private DiveComputerRepository diveComputerRepository;
    @Autowired private DiveComputerManufacturerRepository diveComputerManufacturerRepository;

    private UserEntity owner;
    private UserEntity attacker;
    private long computerId;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new UserEntity("computer-it-owner@test.ch", "hash", "Owner"));
        attacker =
                userRepository.save(new UserEntity("computer-it-attacker@test.ch", "hash", "Atk"));
        final var manufacturer =
                diveComputerManufacturerRepository.save(
                        new DiveComputerManufacturerEntity("Test Manufacturer"));
        final var computer =
                diveComputerRepository.save(
                        new DiveComputerEntity(null, "OWNER-COMPUTER-IT", manufacturer, owner));
        computerId = computer.toRecord().id();
    }

    @Test
    void updateDiveComputerRejectsANonOwner() {
        assertThatThrownBy(
                        () ->
                                diveDataService.updateDiveComputer(
                                        attacker.toRecord(), computerId, "HIJACKED", null))
                .isInstanceOf(ForbiddenException.class);
    }
}
