package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.DiveSiteLink;
import ch.sthomas.stddivelogger.model.dive.DiveSiteType;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DiveService;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Coverage for community-editable dive site metadata (WS6): only a user who's logged at least one
 * dive at a site may edit its description/links/type/maxDepth/countryRegion.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveSiteMetadataIntegrationTest {

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
    @Autowired private DiveSiteRepository diveSiteRepository;

    @Test
    void userWithoutALoggedDiveCannotEditTheSite() {
        final var user =
                userRepository.save(new UserEntity("site-meta-a@test.ch", "hash", "A")).toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Site Meta IT A", new Location(1.0, 1.0).toPoint()))
                        .toRecord();

        assertThatThrownBy(
                        () ->
                                diveService.updateDiveSite(
                                        user,
                                        site.id(),
                                        "desc",
                                        "region",
                                        30.0,
                                        DiveSiteType.WRECK,
                                        List.of()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void userWithALoggedDiveCanEditTheSiteAndLinksArePersisted() {
        final var user =
                userRepository.save(new UserEntity("site-meta-b@test.ch", "hash", "B")).toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Site Meta IT B", new Location(2.0, 2.0).toPoint()))
                        .toRecord();
        diveService.createEmptyDive(
                user,
                new UploadDiveBody(
                        1, "site-meta-it", site.id(), 20.0, Duration.ofMinutes(30), Instant.now()));

        final var updated =
                diveService.updateDiveSite(
                        user,
                        site.id(),
                        "A nice wreck",
                        "Red Sea",
                        35.5,
                        DiveSiteType.WRECK,
                        List.of(new DiveSiteLink(0, "https://example.com", "Info")));

        assertThat(updated.description()).isEqualTo("A nice wreck");
        assertThat(updated.countryRegion()).isEqualTo("Red Sea");
        assertThat(updated.maxDepth()).isEqualTo(35.5);
        assertThat(updated.type()).isEqualTo(DiveSiteType.WRECK);
        assertThat(updated.links()).hasSize(1);
        assertThat(updated.links().getFirst().url()).isEqualTo("https://example.com");
        assertThat(updated.canEdit()).isTrue();

        final var fetched = diveService.getSiteByIdForUser(site.id(), user).orElseThrow();
        assertThat(fetched.links()).hasSize(1);
        assertThat(fetched.canEdit()).isTrue();
    }
}
