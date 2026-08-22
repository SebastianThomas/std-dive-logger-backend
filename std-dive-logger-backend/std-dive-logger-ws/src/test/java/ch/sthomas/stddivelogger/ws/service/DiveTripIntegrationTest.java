package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.DiveTripDataService.DefaultTeamEntry;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripType;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.DiveTripService;

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
 * Coverage for WS7 (dive trips/training groups with nesting): direct dive/sub-trip membership,
 * cycle rejection when nesting would close a loop, transitive dive listing through 2+ nested trips,
 * and default-team seeding when a dive is added to a course-type trip.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveTripIntegrationTest {

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

    @Autowired private DiveTripService diveTripService;
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private ch.sthomas.stddivelogger.model.user.User owner;
    private long siteId;
    private int diveSeq = 0;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("dive-trip-it@test.ch", "hash", "Owner"))
                        .toRecord();
        siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Dive Trip IT Site", new Location(10.0, 10.0).toPoint()))
                        .toRecord()
                        .id();
    }

    private long createDive() {
        final var seq = diveSeq++;
        return diveService
                .createEmptyDive(
                        owner,
                        new UploadDiveBody(
                                seq + 1,
                                "dive-trip-it-" + seq,
                                siteId,
                                18.0,
                                Duration.ofMinutes(25),
                                Instant.now()))
                .id();
    }

    @Test
    void tripsCanNestAndListTransitiveDives() {
        final var season = diveTripService.createTrip(owner, "2026 Season", DiveTripType.TRIP);
        final var greece = diveTripService.createTrip(owner, "Greece Holiday", DiveTripType.TRIP);
        final var course =
                diveTripService.createTrip(owner, "Deco Procedures", DiveTripType.COURSE);

        final var dive1 = createDive();
        final var dive2 = createDive();
        final var dive3 = createDive();

        diveTripService.addDiveMember(owner, greece.id(), dive1, false);
        diveTripService.addDiveMember(owner, greece.id(), dive2, false);
        diveTripService.addDiveMember(owner, course.id(), dive3, false);

        diveTripService.addTripMember(owner, season.id(), greece.id());
        diveTripService.addTripMember(owner, season.id(), course.id());

        final var members = diveTripService.getDirectMembers(owner, season.id());
        assertThat(members).hasSize(2);
        assertThat(members)
                .extracting(DiveTripMember::type)
                .containsOnly(DiveTripMember.MemberType.TRIP);

        final var transitiveDives = diveTripService.getTransitiveDives(owner, season.id(), 0, 20);
        assertThat(transitiveDives.result()).hasSize(3);
        assertThat(transitiveDives.totalElements()).isEqualTo(3);
    }

    @Test
    void addingATripThatWouldCreateACycleIsRejected() {
        final var a = diveTripService.createTrip(owner, "A", DiveTripType.TRIP);
        final var b = diveTripService.createTrip(owner, "B", DiveTripType.TRIP);

        diveTripService.addTripMember(owner, a.id(), b.id());

        assertThatThrownBy(() -> diveTripService.addTripMember(owner, b.id(), a.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonOwnerCannotModifyOrReadATrip() {
        final var stranger =
                userRepository
                        .save(new UserEntity("dive-trip-stranger@test.ch", "hash", "S"))
                        .toRecord();
        final var trip = diveTripService.createTrip(owner, "Private Trip", DiveTripType.TRIP);

        assertThatThrownBy(() -> diveTripService.getTrip(stranger, trip.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(
                        () ->
                                diveTripService.updateTrip(
                                        stranger, trip.id(), "Hacked", DiveTripType.TRIP, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void defaultTeamIsSeededOntoADiveAddedToACourseButNeverOverwritesExistingBuddies() {
        final var course =
                diveTripService.createTrip(owner, "Deco Procedures", DiveTripType.COURSE);
        diveTripService.replaceDefaultTeam(
                owner,
                course.id(),
                List.of(new DefaultTeamEntry(null, "Instructor Alex", BuddyRole.INSTRUCTOR)));

        final var dive1 = createDive();
        diveTripService.addDiveMember(owner, course.id(), dive1, true);
        final var seededDive = diveService.getDiveById(owner, dive1).orElseThrow();
        assertThat(seededDive.namedBuddies()).isNotNull();
        assertThat(seededDive.namedBuddies()).extracting("name").containsExactly("Instructor Alex");

        // Re-seeding a dive that already has named buddies (e.g. a redundant re-add) must not
        // duplicate or clobber what's already there.
        diveTripService.addDiveMember(owner, course.id(), dive1, true);
        assertThat(diveService.getDiveById(owner, dive1).orElseThrow().namedBuddies()).hasSize(1);
    }
}
