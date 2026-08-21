package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Coverage for the {@code DiveBuddyEntity} join-table conversion: linking regardless of which id
 * the caller names first (the DB's {@code CHECK fk_dive_id < fk_buddy_dive_id} used to make this
 * order-sensitive), per-side directional role, unlinking, and the bulk "set this buddy's role
 * everywhere" operation across every dive pair shared with a given user.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveBuddyLinkIntegrationTest {

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

    private User owner;
    private User buddyOwner;
    private long siteId;
    private int diveSequence = 0;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("buddy-link-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        buddyOwner =
                userRepository
                        .save(new UserEntity("buddy-link-it-buddy@test.ch", "hash", "Buddy"))
                        .toRecord();
        siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Buddy Link IT Site", new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
    }

    private long createDive(final User user, final String identifier) {
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
    void linkingWorksRegardlessOfWhichIdIsPassedFirst() {
        // buddyOwner's dive is created first (lower id), owner's second (higher id), then owner
        // links passing THEIR OWN (higher-id) dive first - the case that would have violated
        // t_dive_buddy's old CHECK constraint (fk_dive_id < fk_buddy_dive_id) if linkDive still
        // inserted rows using the caller's argument order verbatim instead of canonicalizing it.
        final var buddyDiveId = createDive(buddyOwner, "buddy-link-lower");
        final var ownerDiveId = createDive(owner, "buddy-link-higher");
        assertThat(ownerDiveId).isGreaterThan(buddyDiveId);
        diveService.addReaders(buddyOwner, buddyDiveId, List.of(owner.id()));

        final var linked = diveService.linkBuddyDive(owner, ownerDiveId, buddyDiveId);
        assertThat(linked.buddiesDives()).extracting("diveId").containsExactly(buddyDiveId);

        // And naming the lower id first (the already-working case) still works too - link two of
        // the owner's own dives together, ownerDiveId (lower of this pair) named first.
        final var thirdDiveId = createDive(owner, "buddy-link-third");
        assertThat(thirdDiveId).isGreaterThan(ownerDiveId);
        final var linkedReverse = diveService.linkBuddyDive(owner, ownerDiveId, thirdDiveId);
        assertThat(linkedReverse.buddiesDives()).extracting("diveId").contains(thirdDiveId);
    }

    @Test
    void roleIsDirectionalPerSideAndUnlinkRemovesIt() {
        final var ownerDiveId = createDive(owner, "buddy-link-role-owner");
        final var buddyDiveId = createDive(buddyOwner, "buddy-link-role-buddy");
        diveService.addReaders(buddyOwner, buddyDiveId, List.of(owner.id()));
        diveService.addReaders(owner, ownerDiveId, List.of(buddyOwner.id()));
        diveService.linkBuddyDive(owner, ownerDiveId, buddyDiveId);

        // Owner rates the buddy as an instructor; the buddy independently rates the owner as less
        // experienced - each side's role must be readable only from its own dive's perspective.
        final var afterOwnerRole =
                diveService.setBuddyDiveRole(owner, ownerDiveId, buddyDiveId, BuddyRole.INSTRUCTOR);
        assertThat(afterOwnerRole.buddiesDives()).hasSize(1);
        assertThat(afterOwnerRole.buddiesDives().getFirst().role()).isEqualTo(BuddyRole.INSTRUCTOR);

        final var afterBuddyRole =
                diveService.setBuddyDiveRole(
                        buddyOwner, buddyDiveId, ownerDiveId, BuddyRole.LESS_EXPERIENCED);
        assertThat(afterBuddyRole.buddiesDives().getFirst().role())
                .isEqualTo(BuddyRole.LESS_EXPERIENCED);

        // The owner's side is unaffected by the buddy's independent rating.
        final var ownerDiveAgain = diveService.getDiveById(owner, ownerDiveId).orElseThrow();
        assertThat(ownerDiveAgain.buddiesDives().getFirst().role()).isEqualTo(BuddyRole.INSTRUCTOR);

        diveService.unlinkBuddyDive(owner, ownerDiveId, buddyDiveId);
        final var afterUnlink = diveService.getDiveById(owner, ownerDiveId).orElseThrow();
        assertThat(afterUnlink.buddiesDives()).isEmpty();
    }

    @Test
    void bulkSetRoleAppliesAcrossEveryLinkedDiveWithThatBuddy() {
        final var ownerDive1 = createDive(owner, "buddy-link-bulk-1");
        final var ownerDive2 = createDive(owner, "buddy-link-bulk-2");
        final var buddyDive1 = createDive(buddyOwner, "buddy-link-bulk-buddy-1");
        final var buddyDive2 = createDive(buddyOwner, "buddy-link-bulk-buddy-2");
        diveService.addReaders(buddyOwner, buddyDive1, List.of(owner.id()));
        diveService.addReaders(buddyOwner, buddyDive2, List.of(owner.id()));
        diveService.linkBuddyDive(owner, ownerDive1, buddyDive1);
        diveService.linkBuddyDive(owner, ownerDive2, buddyDive2);

        diveService.setLinkedBuddyRoleForUser(owner, buddyOwner.id(), BuddyRole.DIVEMASTER);

        final var dive1 = diveService.getDiveById(owner, ownerDive1).orElseThrow();
        final var dive2 = diveService.getDiveById(owner, ownerDive2).orElseThrow();
        assertThat(dive1.buddiesDives().getFirst().role()).isEqualTo(BuddyRole.DIVEMASTER);
        assertThat(dive2.buddiesDives().getFirst().role()).isEqualTo(BuddyRole.DIVEMASTER);
    }

    @Test
    void linkedBuddyUsersAreListedOnceRegardlessOfHowManyDivesAreLinked() {
        final var ownerDive1 = createDive(owner, "buddy-link-users-1");
        final var ownerDive2 = createDive(owner, "buddy-link-users-2");
        final var buddyDive1 = createDive(buddyOwner, "buddy-link-users-buddy-1");
        final var buddyDive2 = createDive(buddyOwner, "buddy-link-users-buddy-2");
        diveService.addReaders(buddyOwner, buddyDive1, List.of(owner.id()));
        diveService.addReaders(buddyOwner, buddyDive2, List.of(owner.id()));
        diveService.linkBuddyDive(owner, ownerDive1, buddyDive1);
        diveService.linkBuddyDive(owner, ownerDive2, buddyDive2);

        final var linkedForOwner = diveService.getLinkedBuddyUsers(owner);
        assertThat(linkedForOwner).extracting("id").containsExactly(buddyOwner.id());

        final var linkedForBuddy = diveService.getLinkedBuddyUsers(buddyOwner);
        assertThat(linkedForBuddy).extracting("id").containsExactly(owner.id());
    }
}
