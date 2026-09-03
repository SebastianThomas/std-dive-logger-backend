package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.conditions.SiteVisibilityLog;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.jspecify.annotations.Nullable;
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

/**
 * Coverage for the per-site visibility scatter feed ({@code GET /v1/dives/sites/{id}/visibility}).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveSiteVisibilityIntegrationTest {

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

    private Dive createDive(final User user, final long siteId, final String id, final Instant at) {
        return diveService.createEmptyDive(
                user, new UploadDiveBody(null, id, siteId, 18.0, Duration.ofMinutes(35), at));
    }

    private void setVisibility(
            final User user,
            final Dive dive,
            final @Nullable Double meters,
            final @Nullable VisibilityFeeling feeling) {
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        dive.id(),
                        dive.number(),
                        null,
                        0,
                        null,
                        null,
                        new Visibility(meters, null, feeling),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null));
    }

    @Test
    void returnsOwnVisibilityLogsAtTheSiteOldestFirst_andRespectsLastYearOnly() {
        final var user =
                userRepository
                        .save(new UserEntity("site-vis-it@test.ch", "hash", "SiteVisIT"))
                        .toRecord();
        final var other =
                userRepository
                        .save(new UserEntity("site-vis-other-it@test.ch", "hash", "SiteVisOther"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(new DiveSiteEntity("Site Vis IT", new Location(47.0, 8.0).toPoint()))
                        .toRecord();
        final var otherSite =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Site Vis IT Other", new Location(48.0, 9.0).toPoint()))
                        .toRecord();

        final var now = Instant.now();
        final var old = createDive(user, site.id(), "vis-old", now.minus(Duration.ofDays(400)));
        final var recent1 =
                createDive(user, site.id(), "vis-recent-1", now.minus(Duration.ofDays(60)));
        final var recent2 =
                createDive(user, site.id(), "vis-recent-2", now.minus(Duration.ofDays(20)));
        createDive(user, site.id(), "vis-none", now.minus(Duration.ofDays(10)));
        final var elsewhere =
                createDive(user, otherSite.id(), "vis-elsewhere", now.minus(Duration.ofDays(5)));
        final var othersDive =
                createDive(other, site.id(), "vis-others", now.minus(Duration.ofDays(3)));

        setVisibility(user, old, 12.0, VisibilityFeeling.LOW);
        setVisibility(user, recent1, 25.0, null);
        setVisibility(user, recent2, null, VisibilityFeeling.HIGH); // feeling only, still counts
        // vis-none: left untouched -> excluded
        setVisibility(user, elsewhere, 8.0, null); // different site -> excluded
        setVisibility(other, othersDive, 5.0, null); // other user -> excluded

        final var all = diveService.getSiteVisibilityLogs(user, site.id(), false);
        assertThat(all)
                .extracting(SiteVisibilityLog::diveIdentifier)
                .containsExactly("vis-old", "vis-recent-1", "vis-recent-2");
        assertThat(all.getFirst().meters()).isEqualTo(12.0);
        assertThat(all.getFirst().feeling()).isEqualTo(VisibilityFeeling.LOW);
        assertThat(all.get(2).meters()).isNull();
        assertThat(all.get(2).feeling()).isEqualTo(VisibilityFeeling.HIGH);

        final var lastYear = diveService.getSiteVisibilityLogs(user, site.id(), true);
        assertThat(lastYear)
                .extracting(SiteVisibilityLog::diveIdentifier)
                .containsExactly("vis-recent-1", "vis-recent-2");
    }
}
