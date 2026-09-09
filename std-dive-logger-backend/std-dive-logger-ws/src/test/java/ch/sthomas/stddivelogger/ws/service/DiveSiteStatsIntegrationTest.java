package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveSiteStatsDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.DiveSiteStatsService;

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
import java.util.NoSuchElementException;

/** Coverage for global site aggregates shared by site detail and site suggestions. */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveSiteStatsIntegrationTest {

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
    @Autowired private DiveSiteStatsService statsService;
    @Autowired private DiveSiteStatsDataService statsDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    @Test
    void combinesSuggestionAggregatesWithAllDiverMonthlyDepthActivity() {
        final User first =
                userRepository
                        .save(new UserEntity("site-stats-a@test.ch", "hash", "StatsA"))
                        .toRecord();
        final User second =
                userRepository
                        .save(new UserEntity("site-stats-b@test.ch", "hash", "StatsB"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Site Stats IT", new Location(47.0, 8.0).toPoint()))
                        .toRecord();

        createDive(first, site.id(), "jan-a", 12.0, Instant.parse("2026-01-05T10:00:00Z"));
        createDive(second, site.id(), "jan-b", 24.0, Instant.parse("2026-01-20T10:00:00Z"));
        createDive(first, site.id(), "feb-a", 30.0, Instant.parse("2026-02-10T10:00:00Z"));
        statsDataService.refreshAll();

        final var stats = statsService.getForSite(site.id());

        assertThat(stats.totalDives()).isEqualTo(3);
        assertThat(stats.distinctDivers()).isEqualTo(2);
        assertThat(stats.averageMaxDepth()).isEqualTo(22.0);
        assertThat(stats.shallowestMaxDepth()).isEqualTo(12.0);
        assertThat(stats.deepestMaxDepth()).isEqualTo(30.0);
        assertThat(stats.monthlyActivity()).hasSize(2);
        assertThat(stats.monthlyActivity().getFirst().diveCount()).isEqualTo(2);
        assertThat(stats.monthlyActivity().getFirst().distinctDivers()).isEqualTo(2);
        assertThat(stats.monthlyActivity().getFirst().averageMaxDepth()).isEqualTo(18.0);
        assertThat(stats.monthlyActivity().getFirst().deepestMaxDepth()).isEqualTo(24.0);
        assertThat(stats.monthlyActivity().get(1).diveCount()).isEqualTo(1);
    }

    @Test
    void rejectsAStatsRequestForAMissingSite() {
        assertThatThrownBy(() -> statsService.getForSite(Long.MAX_VALUE))
                .isInstanceOf(NoSuchElementException.class);
    }

    private void createDive(
            final User user,
            final long siteId,
            final String identifier,
            final double maxDepth,
            final Instant start) {
        diveService.createEmptyDive(
                user,
                new UploadDiveBody(
                        null, identifier, siteId, maxDepth, Duration.ofMinutes(40), start));
    }
}
