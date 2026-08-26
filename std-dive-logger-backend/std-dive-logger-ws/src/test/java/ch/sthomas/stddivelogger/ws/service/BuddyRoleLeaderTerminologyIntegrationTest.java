package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.NamedBuddyInput;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.DiveLeader;
import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Coverage for Workstream 9: named-buddy roles, dive-leader resolution (self/named/linked), and the
 * dive-level buddy/team terminology override.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class BuddyRoleLeaderTerminologyIntegrationTest {

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

    private User owner;
    private User buddyOwner;
    private long siteId;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("buddy-role-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        buddyOwner =
                userRepository
                        .save(new UserEntity("buddy-role-it-buddy@test.ch", "hash", "Buddy"))
                        .toRecord();
        siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Buddy Role IT Site", new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
    }

    private int diveSequence = 0;

    private long createDive(final User user, final String identifier) {
        // Distinct dive numbers/start times per call - getNextDiveNumber() can return the same
        // value twice within one uncommitted transaction, and the synthetic "Manual" computer is
        // shared per-user (t_dive_profiles is unique on (computer, start)).
        final var sequence = diveSequence++;
        final var start = Instant.parse("2026-06-01T09:00:00Z").plusSeconds(3600L * sequence);
        return diveService
                .createEmptyDive(
                        user,
                        new UploadDiveBody(
                                sequence + 1,
                                identifier,
                                siteId,
                                20.0,
                                Duration.ofMinutes(30),
                                start))
                .id();
    }

    @Test
    void namedBuddyRolePersistsAndDefaultsToSelfLeaderAndBuddyTerminology() {
        final var diveId = createDive(owner, "buddy-role-named");

        final var updated =
                diveService.updateDive(
                        owner,
                        new UpdateDiveBody(
                                diveId,
                                1,
                                null,
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(new NamedBuddyInput("Alex", BuddyRole.INSTRUCTOR)),
                                null,
                                null,
                                null,
                                null,
                                false,
                                null,
                                null));

        assertThat(updated.namedBuddies()).hasSize(1);
        final var namedBuddy = updated.namedBuddies().getFirst();
        assertThat(namedBuddy.name()).isEqualTo("Alex");
        assertThat(namedBuddy.role()).isEqualTo(BuddyRole.INSTRUCTOR);

        // No leader/terminology set yet - unset, not resolved to the owner leading.
        assertThat(updated.leader()).isEqualTo(DiveLeader.UNSET);
        assertThat(updated.teamTerminology()).isNull();

        final var namedBuddyId = namedBuddy.id();
        final var withLeaderAndTerminology =
                diveService.updateDive(
                        owner,
                        new UpdateDiveBody(
                                diveId,
                                1,
                                null,
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(new NamedBuddyInput("Alex", BuddyRole.INSTRUCTOR)),
                                null,
                                null,
                                namedBuddyId,
                                null,
                                false,
                                TeamTerminology.TEAM,
                                null));

        assertThat(withLeaderAndTerminology.leader().type()).isEqualTo(DiveLeader.LeaderType.NAMED);
        assertThat(withLeaderAndTerminology.leader().namedBuddyId()).isEqualTo(namedBuddyId);
        assertThat(withLeaderAndTerminology.teamTerminology()).isEqualTo(TeamTerminology.TEAM);
    }

    @Test
    void linkedBuddyCanBeSetAsLeader() {
        final var ownerDiveId = createDive(owner, "buddy-role-linked-owner");
        final var buddyDiveId = createDive(buddyOwner, "buddy-role-linked-buddy");
        // linkBuddyDive requires the caller already have read access to the dive being linked -
        // grant it explicitly first, same as a real "share then link" flow would.
        diveService.addReaders(buddyOwner, buddyDiveId, List.of(owner.id()));

        diveService.linkBuddyDive(owner, ownerDiveId, buddyDiveId);

        final var updated =
                diveService.updateDive(
                        owner,
                        new UpdateDiveBody(
                                ownerDiveId,
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
                                buddyDiveId,
                                false,
                                null,
                                null));

        assertThat(updated.leader().type()).isEqualTo(DiveLeader.LeaderType.LINKED);
        assertThat(updated.leader().linkedDiveId()).isEqualTo(buddyDiveId);
    }

    @Test
    void settingBothLeaderReferencesIsRejected() {
        final var diveId = createDive(owner, "buddy-role-conflict");
        assertThatThrownBy(
                        () ->
                                new UpdateDiveBody(
                                        diveId, 1, null, 0, null, null, null, null, null, null,
                                        null, null, 1L, 2L, false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namedBuddyFromAnotherDiveCannotBeSetAsLeader() {
        final var diveId = createDive(owner, "buddy-role-foreign-leader");
        final var otherDiveId = createDive(owner, "buddy-role-foreign-leader-other");
        final var otherDiveWithBuddy =
                diveService.updateDive(
                        owner,
                        new UpdateDiveBody(
                                otherDiveId,
                                2,
                                null,
                                0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(new NamedBuddyInput("Foreign", null)),
                                null,
                                null,
                                null,
                                null,
                                false,
                                null,
                                null));
        final var foreignBuddyId = otherDiveWithBuddy.namedBuddies().getFirst().id();

        assertThatThrownBy(
                        () ->
                                diveService.updateDive(
                                        owner,
                                        new UpdateDiveBody(
                                                diveId,
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
                                                foreignBuddyId,
                                                null,
                                                false,
                                                null,
                                                null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
