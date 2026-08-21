package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.conditions.Current;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
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
import java.util.Objects;

/**
 * Regression/coverage for water type and current strength (see {@code DiveConditionsEntity}) - both
 * are optional, created lazily on first edit (mirroring {@code VisibilityEntity}'s own
 * lazy-creation pattern), so this exercises both the "not yet set" and "set then updated" paths.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveConditionsUpdateIntegrationTest {

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

    @Test
    void settingAndUpdatingWaterTypeAndCurrentPersists() {
        final var userEntity =
                userRepository.save(new UserEntity("conditions-it@test.ch", "hash", "IT"));
        final var user = userEntity.toRecord();
        final var site =
                diveSiteRepository.save(
                        new DiveSiteEntity(
                                "Conditions IT Site", new Location(47.0, 8.0).toPoint()));

        final var created =
                diveService.createEmptyDive(
                        user,
                        new UploadDiveBody(
                                null,
                                "conditions-it",
                                site.toRecord().id(),
                                20.0,
                                Duration.ofMinutes(30),
                                Instant.parse("2026-06-01T09:00:00Z")));
        assertThat(created.waterType()).isNull();
        assertThat(created.current()).isNull();

        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        created.id(),
                        created.number(),
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        WaterType.SALT,
                        new Current(1.5, "Mild down-current", 2),
                        null,
                        null,
                        null));

        final var afterFirstUpdate = diveService.getDiveById(user, created.id()).orElseThrow();
        assertThat(afterFirstUpdate.waterType()).isEqualTo(WaterType.SALT);
        final var firstCurrent = Objects.requireNonNull(afterFirstUpdate.current());
        assertThat(firstCurrent.knots()).isEqualTo(1.5);
        assertThat(firstCurrent.description()).isEqualTo("Mild down-current");
        assertThat(firstCurrent.feeling()).isEqualTo(2);

        // Update the already-persisted conditions row in place (not insert-again).
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        created.id(),
                        created.number(),
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        WaterType.FRESH,
                        new Current(0.5, "Negligible", 0),
                        null,
                        null,
                        null));

        final var afterSecondUpdate = diveService.getDiveById(user, created.id()).orElseThrow();
        assertThat(afterSecondUpdate.waterType()).isEqualTo(WaterType.FRESH);
        final var secondCurrent = Objects.requireNonNull(afterSecondUpdate.current());
        assertThat(secondCurrent.knots()).isEqualTo(0.5);
        assertThat(secondCurrent.feeling()).isEqualTo(0);
    }
}
