package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DiveService;

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

import java.time.Duration;
import java.time.Instant;

/**
 * Coverage for WS9's "smart terminology default" - the user's own most recent explicit BUDDY/TEAM
 * choice, used to prefill a new/unset dive's terminology picker.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class TeamTerminologyDefaultIntegrationTest {

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
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private long createDive(
            final ch.sthomas.stddivelogger.model.user.User user,
            final long siteId,
            final int number) {
        return diveService
                .createEmptyDive(
                        user,
                        new UploadDiveBody(
                                number,
                                "terminology-default-it-" + number,
                                siteId,
                                10.0,
                                Duration.ofMinutes(10),
                                Instant.now()))
                .id();
    }

    @Test
    void isEmptyForAUserWhoHasNeverSetOne() {
        final var user =
                userRepository
                        .save(new UserEntity("terminology-default-a@test.ch", "hash", "A"))
                        .toRecord();
        assertThat(diveService.getMostRecentTeamTerminology(user)).isEmpty();
    }

    @Test
    void reflectsTheMostRecentlyCreatedDiveWithAnExplicitChoice() {
        final var user =
                userRepository
                        .save(new UserEntity("terminology-default-b@test.ch", "hash", "B"))
                        .toRecord();
        final var siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Terminology Default IT Site",
                                        new Location(4.0, 4.0).toPoint()))
                        .toRecord()
                        .id();

        final var dive1 = createDive(user, siteId, 1);
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        dive1,
                        1,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TeamTerminology.TEAM));

        assertThat(diveService.getMostRecentTeamTerminology(user)).contains(TeamTerminology.TEAM);

        final var dive2 = createDive(user, siteId, 2);
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        dive2,
                        2,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        TeamTerminology.BUDDY));

        assertThat(diveService.getMostRecentTeamTerminology(user)).contains(TeamTerminology.BUDDY);
    }
}
