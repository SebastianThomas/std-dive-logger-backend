package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Re-dating a dive that was imported from a dive-computer file (clock was wrong) - shifts every
 * profile + measurement by one delta, keeps max depth / duration, and still rejects a shift that
 * would collide with another dive on the same computer.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveReDateIntegrationTest {

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

    @Autowired private ImportService importService;
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;

    private static MockMultipartFile fixture(final String filename) throws IOException {
        try (final var in =
                DiveReDateIntegrationTest.class.getClassLoader().getResourceAsStream(filename)) {
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/octet-stream",
                    Objects.requireNonNull(in).readAllBytes());
        }
    }

    private Dive importFixture(final User user, final String fixture, final String siteName)
            throws IOException {
        final var staged = importService.stageUpload(user, List.of(fixture(fixture)));
        assertThat(staged.errors()).isEmpty();
        final var committed =
                importService.commit(
                        user,
                        staged.staged().getFirst().id(),
                        new PendingImportCommitRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                siteName,
                                new Location(1.0, 2.0),
                                null,
                                null));
        return diveService.getDiveById(user, committed.id()).orElseThrow();
    }

    @Test
    void reDatingAnImportedDiveShiftsEveryProfileTimeButKeepsDepthAndDuration() throws IOException {
        final var user =
                userRepository
                        .save(new UserEntity("redate-it@test.ch", "hash", "ReDateIT"))
                        .toRecord();
        final var dive = importFixture(user, "suunto-eon-core-dive-1-deco.fit", "ReDate IT Site");

        final var originalStart = dive.summary().start();
        final var originalEnd = dive.summary().end();
        final var originalMaxDepth = dive.summary().maxDepth();
        final var originalFirstMeasurement =
                Objects.requireNonNull(dive.profiles().getFirst().measurements()).getFirst();

        final var newStart = originalStart.minus(Duration.ofDays(3)).minus(Duration.ofHours(2));
        final var shift = Duration.between(originalStart, newStart);

        final var redated = diveService.setDiveStartTime(user, dive.id(), newStart);

        assertThat(redated.summary().start()).isEqualTo(newStart);
        assertThat(redated.summary().end()).isEqualTo(originalEnd.plus(shift));
        assertThat(redated.summary().maxDepth()).isEqualTo(originalMaxDepth);
        assertThat(redated.summary().bottomTime()).isEqualTo(dive.summary().bottomTime());
        assertThat(redated.profiles()).hasSize(1);
        assertThat(redated.profiles().getFirst().start()).isEqualTo(newStart);
        final var shiftedFirstMeasurement =
                Objects.requireNonNull(redated.profiles().getFirst().measurements()).getFirst();
        assertThat(shiftedFirstMeasurement.measurement().time())
                .isEqualTo(originalFirstMeasurement.measurement().time().plus(shift));
    }

    @Test
    void reDatingOntoAnotherDivesStartOnTheSameComputerIsRejected() throws IOException {
        final var user =
                userRepository
                        .save(
                                new UserEntity(
                                        "redate-collide-it@test.ch", "hash", "ReDateCollideIT"))
                        .toRecord();
        // Two different real dives from the same Suunto EON Core - the FIT export carries no
        // serial,
        // so both resolve to one shared computer, and re-dating one onto the other's start would
        // violate t_dive_profiles' (fk_dive_computer, dive_profile_start) unique constraint.
        final var diveA =
                importFixture(user, "suunto-eon-core-dive-1-deco.fit", "ReDate Collide A");
        final var diveB =
                importFixture(user, "suunto-eon-core-dive-2-nodeco.fit", "ReDate Collide B");
        assertThat(diveB.profiles().getFirst().diveComputer().id())
                .as("both fixtures should resolve to one shared computer")
                .isEqualTo(diveA.profiles().getFirst().diveComputer().id());

        assertThatThrownBy(
                        () ->
                                diveService.setDiveStartTime(
                                        user, diveB.id(), diveA.summary().start()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already starts at that exact time");
    }
}
