package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.NamedBuddyInput;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.StatsService;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Coverage for the buddy-role stats breakdown (overall/by-buddy/by-site/by-year/by-month), computed
 * over each dive's already-resolved per-viewpoint buddy roles (named and linked).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class BuddyRoleStatsIntegrationTest {

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
    @Autowired private StatsService statsService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private User owner;
    private long siteAId;
    private long siteBId;
    private int diveSequence = 0;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("buddy-role-stats-it@test.ch", "hash", "Owner"))
                        .toRecord();
        siteAId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Buddy Role Stats Site A",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
        siteBId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Buddy Role Stats Site B",
                                        new Location(46.0, 7.0).toPoint()))
                        .toRecord()
                        .id();
    }

    private final java.util.Map<Long, Integer> diveNumbers = new java.util.HashMap<>();

    private long createDive(final long siteId, final Instant start) {
        final var sequence = diveSequence++;
        final var dive =
                diveService.createEmptyDive(
                        owner,
                        new UploadDiveBody(
                                sequence + 1,
                                "buddy-role-stats-" + sequence,
                                siteId,
                                20.0,
                                Duration.ofMinutes(30),
                                start));
        diveNumbers.put(dive.id(), dive.number());
        return dive.id();
    }

    private void setBuddyRole(final long diveId, final String buddyName, final BuddyRole role) {
        updateNamedBuddies(diveId, List.of(new NamedBuddyInput(buddyName, role)));
    }

    private void updateNamedBuddies(final long diveId, final List<NamedBuddyInput> buddies) {
        diveService.updateDive(
                owner,
                new UpdateDiveBody(
                        diveId,
                        java.util.Objects.requireNonNull(diveNumbers.get(diveId)),
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        buddies,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null));
    }

    @Test
    void breakdownsAggregateRoleAssignmentsAcrossDives() {
        final var dive1 = createDive(siteAId, Instant.parse("2025-06-01T09:00:00Z"));
        final var dive2 = createDive(siteAId, Instant.parse("2025-07-01T09:00:00Z"));
        final var dive3 = createDive(siteBId, Instant.parse("2026-06-01T09:00:00Z"));

        // dive1: Alex=INSTRUCTOR. dive2: Alex=INSTRUCTOR, Sam=LESS_EXPERIENCED. dive3:
        // Alex=DIVEMASTER.
        setBuddyRole(dive1, "Alex", BuddyRole.INSTRUCTOR);
        updateNamedBuddies(
                dive2,
                List.of(
                        new NamedBuddyInput("Alex", BuddyRole.INSTRUCTOR),
                        new NamedBuddyInput("Sam", BuddyRole.LESS_EXPERIENCED)));
        setBuddyRole(dive3, "Alex", BuddyRole.DIVEMASTER);

        final var stats = statsService.getBuddyRoleStats(owner);

        assertThat(stats.overall())
                .extracting("role")
                .contains(BuddyRole.INSTRUCTOR, BuddyRole.LESS_EXPERIENCED, BuddyRole.DIVEMASTER);
        assertThat(
                        stats.overall().stream()
                                .filter(c -> c.role() == BuddyRole.INSTRUCTOR)
                                .findFirst())
                .isPresent()
                .get()
                .extracting("count")
                .isEqualTo(2L);

        assertThat(stats.byBuddy()).extracting("group").contains("Alex", "Sam");
        final var alexBreakdown =
                stats.byBuddy().stream()
                        .filter(b -> b.group().equals("Alex"))
                        .findFirst()
                        .orElseThrow();
        assertThat(alexBreakdown.total()).isEqualTo(3L);

        assertThat(stats.bySite())
                .extracting("group")
                .contains("Buddy Role Stats Site A", "Buddy Role Stats Site B");
        final var siteABreakdown =
                stats.bySite().stream()
                        .filter(b -> b.group().equals("Buddy Role Stats Site A"))
                        .findFirst()
                        .orElseThrow();
        assertThat(siteABreakdown.total()).isEqualTo(3L);

        assertThat(stats.byYear()).extracting("group").contains("2025", "2026");
        final var year2025 =
                stats.byYear().stream()
                        .filter(b -> b.group().equals("2025"))
                        .findFirst()
                        .orElseThrow();
        assertThat(year2025.total()).isEqualTo(3L);

        assertThat(stats.byMonth()).extracting("group").contains("2025-06", "2025-07", "2026-06");
    }
}
