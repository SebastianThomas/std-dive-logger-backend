package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.NamedBuddyInput;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
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
import java.util.Objects;
import java.util.Optional;

/**
 * Coverage for saved per-buddy default roles: applied automatically the first time a buddy is newly
 * added to a dive (manually or via import), but never re-applied over an explicit choice - see
 * DiveDataService#applyDefaultBuddyRoles and #getOldOrNewBuddy.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveBuddyDefaultRoleIntegrationTest {

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
    private int diveSequence = 0;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(
                                new UserEntity(
                                        "buddy-default-role-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        buddyOwner =
                userRepository
                        .save(
                                new UserEntity(
                                        "buddy-default-role-it-buddy@test.ch", "hash", "Buddy"))
                        .toRecord();
        siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Buddy Default Role IT Site",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
    }

    private long createEmptyDive(final User user, final String identifier) {
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

    private UpdateDiveBody withNamedBuddies(
            final long diveId, final List<NamedBuddyInput> namedBuddies) {
        return new UpdateDiveBody(
                diveId,
                1,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                namedBuddies,
                null,
                null,
                null,
                null,
                false,
                null,
                null);
    }

    @Test
    void newlyAddedNamedBuddyGetsTheSavedDefaultRole() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        final var diveId = createEmptyDive(owner, "default-role-named");

        final var updated =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(diveId, List.of(new NamedBuddyInput("Alex", null))));

        assertThat(updated.namedBuddies()).hasSize(1);
        assertThat(updated.namedBuddies().getFirst().role()).isEqualTo(BuddyRole.INSTRUCTOR);
    }

    @Test
    void explicitRoleOverridesTheSavedDefaultWhenAddingABuddy() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        final var diveId = createEmptyDive(owner, "default-role-explicit-wins");

        final var updated =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(
                                diveId,
                                List.of(new NamedBuddyInput("Alex", BuddyRole.DIVEMASTER))));

        assertThat(updated.namedBuddies().getFirst().role()).isEqualTo(BuddyRole.DIVEMASTER);
    }

    @Test
    void buddyWithNoSavedDefaultStaysRoleless() {
        final var diveId = createEmptyDive(owner, "default-role-none-saved");

        final var updated =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(diveId, List.of(new NamedBuddyInput("Casey", null))));

        assertThat(updated.namedBuddies().getFirst().role()).isNull();
    }

    @Test
    void deliberatelyClearingAnExistingBuddysRoleIsNeverRefilledFromTheDefault() {
        final var diveId = createEmptyDive(owner, "default-role-explicit-clear");
        final var withRole =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(
                                diveId,
                                List.of(new NamedBuddyInput("Alex", BuddyRole.DIVEMASTER))));
        assertThat(withRole.namedBuddies().getFirst().role()).isEqualTo(BuddyRole.DIVEMASTER);

        // A default saved *after* the buddy already exists on this dive must not retroactively
        // resurface when the diver explicitly clears this dive's role back to unset.
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        final var cleared =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(diveId, List.of(new NamedBuddyInput("Alex", null))));

        assertThat(cleared.namedBuddies().getFirst().role()).isNull();
    }

    @Test
    void savedDefaultRoleAppliesToANewlyImportedDivesBuddies() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.EQUAL_EXPERIENCE);
        final var computerId =
                diveService
                        .getOrCreateDiveComputer(
                                owner, "Test Manufacturer", "SN-1", "Test Computer")
                        .id();
        final var start = Instant.parse("2026-06-01T09:00:00Z");
        final var end = start.plusSeconds(1800);
        final var measurements =
                List.of(
                        new DiveMeasurement(
                                start, null, 20.0, null, null, null, null, null, null, null, null,
                                null, null),
                        new DiveMeasurement(
                                end, null, 20.0, null, null, null, null, null, null, null, null,
                                null, null));
        final var profile = new DiveProfileUpload(computerId, start, end, measurements);

        final var result =
                diveService.saveDive(
                        owner,
                        Optional.of(1),
                        "default-role-import",
                        "",
                        null,
                        null,
                        null,
                        siteId,
                        List.of(profile),
                        List.of("Alex"));

        assertThat(result.isException()).isFalse();
        final var dive = diveService.getDiveById(owner, result.value().id()).orElseThrow();
        assertThat(dive.namedBuddies()).hasSize(1);
        assertThat(dive.namedBuddies().getFirst().role()).isEqualTo(BuddyRole.EQUAL_EXPERIENCE);
    }

    @Test
    void clearingASavedDefaultStopsItFromApplying() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        diveService.setDefaultNamedBuddyRole(owner, "Alex", null);
        final var diveId = createEmptyDive(owner, "default-role-cleared-default");

        final var updated =
                diveService.updateDive(
                        owner,
                        withNamedBuddies(diveId, List.of(new NamedBuddyInput("Alex", null))));

        assertThat(updated.namedBuddies().getFirst().role()).isNull();
    }

    @Test
    void defaultRolesAreScopedPerUser() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        final var diveId = createEmptyDive(buddyOwner, "default-role-other-user");

        final var updated =
                diveService.updateDive(
                        buddyOwner,
                        withNamedBuddies(diveId, List.of(new NamedBuddyInput("Alex", null))));

        assertThat(updated.namedBuddies().getFirst().role()).isNull();
    }

    @Test
    void linkingTwoDivesAppliesEachSidesOwnSavedDefaultIndependently() {
        diveService.setDefaultLinkedBuddyRole(owner, buddyOwner.id(), BuddyRole.LESS_EXPERIENCED);
        final var ownerDiveId = createEmptyDive(owner, "default-role-link-owner");
        final var buddyDiveId = createEmptyDive(buddyOwner, "default-role-link-buddy");
        diveService.addReaders(buddyOwner, buddyDiveId, List.of(owner.id()));

        diveService.linkBuddyDive(owner, ownerDiveId, buddyDiveId);

        final var ownerSide = diveService.getDiveById(owner, ownerDiveId).orElseThrow();
        assertThat(ownerSide.buddiesDives()).hasSize(1);
        assertThat(ownerSide.buddiesDives().getFirst().role())
                .isEqualTo(BuddyRole.LESS_EXPERIENCED);

        // buddyOwner never saved a default for owner - their own side of the link stays role-less.
        final var buddySide = diveService.getDiveById(buddyOwner, buddyDiveId).orElseThrow();
        assertThat(buddySide.buddiesDives().getFirst().role()).isNull();
    }

    @Test
    void savedDefaultRolesAreListedTogether() {
        diveService.setDefaultNamedBuddyRole(owner, "Alex", BuddyRole.INSTRUCTOR);
        diveService.setDefaultLinkedBuddyRole(owner, buddyOwner.id(), BuddyRole.LESS_EXPERIENCED);

        final var defaults = diveService.getDefaultBuddyRoles(owner);

        assertThat(defaults).hasSize(2);
        assertThat(defaults)
                .anySatisfy(
                        d -> {
                            assertThat(d.buddyName()).isEqualTo("Alex");
                            assertThat(d.role()).isEqualTo(BuddyRole.INSTRUCTOR);
                        });
        assertThat(defaults)
                .anySatisfy(
                        d -> {
                            final var buddyUser = d.buddyUser();
                            assertThat(buddyUser).isNotNull();
                            assertThat(Objects.requireNonNull(buddyUser).id())
                                    .isEqualTo(buddyOwner.id());
                            assertThat(d.role()).isEqualTo(BuddyRole.LESS_EXPERIENCED);
                        });
    }
}
