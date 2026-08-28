package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.DiveBackfillField;
import ch.sthomas.stddivelogger.model.dive.conditions.Current;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.VisibilityFeeling;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
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

/**
 * Coverage for the "backfill" guide: the queue of dives still missing at least one checklist item
 * (visibility, gas consumption, water type, leader, notes), ordered most-incomplete/oldest first,
 * plus the per-(dive, reason) "no more info to add" dismissal that removes a gap from the queue.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DiveBackfillIntegrationTest {

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

    private long createDive(
            final User user, final long siteId, final int number, final Instant start) {
        return diveService
                .createEmptyDive(
                        user,
                        new UploadDiveBody(
                                number,
                                "backfill-it-" + number,
                                siteId,
                                10.0,
                                Duration.ofMinutes(10),
                                start))
                .id();
    }

    private User newUser(final String email) {
        // Name has its own unique constraint - derive a distinct one per email.
        final var name = email.substring(0, email.indexOf('@'));
        return userRepository.save(new UserEntity(email, "hash", name)).toRecord();
    }

    private long newSite(final String name) {
        return diveSiteRepository
                .save(new DiveSiteEntity(name, new Location(47.0, 8.0).toPoint()))
                .toRecord()
                .id();
    }

    private static UpdateDiveBody notesOnly(
            final long diveId, final int number, final String notes) {
        return new UpdateDiveBody(
                diveId, number, notes, 0, null, null, null, null, null, null, null, null, null,
                null, false, null, null);
    }

    @Test
    void queuesIncompleteDivesMostMissingAndOldestFirst() {
        final var user = newUser("backfill-it@test.ch");
        final var siteId = newSite("Backfill IT Site");

        final var completeId = createDive(user, siteId, 1, Instant.parse("2026-01-01T09:00:00Z"));
        final var partialId = createDive(user, siteId, 2, Instant.parse("2026-02-01T09:00:00Z"));
        final var emptyId = createDive(user, siteId, 3, Instant.parse("2026-03-01T09:00:00Z"));

        // Fully backfilled - fills in every checklist item in one go (conditions/waterType are
        // always applied unconditionally by DiveDataService.updateDive, so a second partial call
        // here would silently wipe them back to null).
        diveService.updateDive(
                user,
                new UpdateDiveBody(
                        completeId,
                        1,
                        "Great dive",
                        0,
                        null,
                        new DiveGasConsumption(18.0, 12.0, 1800.0),
                        new Visibility(15.0, "Clear", VisibilityFeeling.HIGH),
                        null,
                        null,
                        null,
                        WaterType.SALT,
                        new Current(0.5, "Mild", 1),
                        null,
                        null,
                        true,
                        null,
                        null));

        // Only notes filled in - still missing visibility/gas/waterType/leader.
        diveService.updateDive(user, notesOnly(partialId, 2, "Partial notes"));

        final var queue = diveService.getBackfillQueue(user);

        assertThat(queue).extracting("diveId").containsExactly(emptyId, partialId);
        assertThat(queue.get(0).missingCount()).isEqualTo(5);
        assertThat(queue.get(1).missingCount()).isEqualTo(4);
        assertThat(queue.get(1).missingFields()).doesNotContain(DiveBackfillField.NOTES);
    }

    @Test
    void dismissingReasonsMovesDiveOutOfTheActiveQueue() {
        final var user = newUser("backfill-dismiss@test.ch");
        final var diveId =
                createDive(user, newSite("Dismiss Site"), 1, Instant.parse("2026-01-01T09:00:00Z"));

        // Dismiss one reason: still 4 outstanding, still active.
        diveService.setBackfillDismissed(user, diveId, DiveBackfillField.VISIBILITY, true);
        var status = diveService.getBackfillStatus(user, diveId);
        assertThat(status.outstandingCount()).isEqualTo(4);
        assertThat(status.fullyDismissed()).isFalse();

        // Dismiss the whole dive (all remaining) - now fully dismissed, sinks in the queue.
        diveService.setBackfillDismissed(user, diveId, null, true);
        status = diveService.getBackfillStatus(user, diveId);
        assertThat(status.outstandingCount()).isZero();
        assertThat(status.fullyDismissed()).isTrue();
        assertThat(diveService.getBackfillQueue(user).get(0).fullyDismissed()).isTrue();

        // Restore the whole dive - every gap is back.
        diveService.setBackfillDismissed(user, diveId, null, false);
        assertThat(diveService.getBackfillStatus(user, diveId).outstandingCount()).isEqualTo(5);
    }

    @Test
    void aReasonWithNoDismissalRowStillSurfaces() {
        final var user = newUser("backfill-perpair@test.ch");
        final var diveId =
                createDive(
                        user, newSite("Per-pair Site"), 1, Instant.parse("2026-01-01T09:00:00Z"));

        // Dismiss NOTES only - LEADER (and the rest) have no dismissal row, so the dive stays
        // active. This is the core of the per-(dive, reason) design: adding a new backfillable
        // field later means every old dive surfaces it with no extra bookkeeping.
        diveService.setBackfillDismissed(user, diveId, DiveBackfillField.NOTES, true);
        final var status = diveService.getBackfillStatus(user, diveId);
        assertThat(status.dismissedFields()).containsExactly(DiveBackfillField.NOTES);
        assertThat(status.outstandingFields()).contains(DiveBackfillField.LEADER);
        assertThat(status.fullyDismissed()).isFalse();
    }

    @Test
    void dismissAllClearsTheActiveQueueAndPerReasonBulkDropsOnlyThatReason() {
        final var user = newUser("backfill-bulk@test.ch");
        final var siteId = newSite("Bulk Site");
        createDive(user, siteId, 1, Instant.parse("2026-01-01T09:00:00Z"));
        createDive(user, siteId, 2, Instant.parse("2026-02-01T09:00:00Z"));

        // Per-reason bulk: WATER_TYPE gone from every dive's outstanding set, others untouched.
        var queue = diveService.dismissAllBackfill(user, DiveBackfillField.WATER_TYPE);
        assertThat(queue)
                .allSatisfy(
                        s ->
                                assertThat(s.outstandingFields())
                                        .doesNotContain(DiveBackfillField.WATER_TYPE))
                .allSatisfy(
                        s -> assertThat(s.outstandingFields()).contains(DiveBackfillField.NOTES));

        // Whole-queue bulk: nothing left active.
        queue = diveService.dismissAllBackfill(user, null);
        assertThat(queue).allSatisfy(s -> assertThat(s.fullyDismissed()).isTrue());
    }

    @Test
    void cannotTouchAnotherUsersDiveBackfill() {
        final var owner = newUser("backfill-owner@test.ch");
        final var stranger = newUser("backfill-stranger@test.ch");
        final var diveId =
                createDive(owner, newSite("Owner Site"), 1, Instant.parse("2026-01-01T09:00:00Z"));

        assertThatThrownBy(() -> diveService.getBackfillStatus(stranger, diveId))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> diveService.setBackfillDismissed(stranger, diveId, null, true))
                .isInstanceOf(ForbiddenException.class);
    }
}
