package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Real end-to-end "reimport in place" flow: import the real Suunto FIT fixture (no TTS data), then
 * reimport the same physical dive's JSON export (real TTS) onto the same profile, and confirm the
 * backfill actually lands - the concrete motivating use case for this whole feature. Also covers
 * the safety check rejecting an unrelated dive.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class ReimportProfileIntegrationTest {

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

    @Autowired private ImportService importService;
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;

    private User createTestUser(final String email) {
        return userRepository.save(new UserEntity(email, "hash", "IT")).toRecord();
    }

    private static MockMultipartFile fixture(final String filename) throws IOException {
        try (final var in =
                ReimportProfileIntegrationTest.class
                        .getClassLoader()
                        .getResourceAsStream(filename)) {
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/octet-stream",
                    Objects.requireNonNull(in).readAllBytes());
        }
    }

    @Test
    void reimportingTheJsonExportOfTheSameFitImportedDiveBackfillsRealTts() throws IOException {
        final var user = createTestUser("reimport-it-1@test.ch");
        final var stagedFit =
                importService.stageUpload(
                        user, List.of(fixture("suunto-eon-core-dive-1-deco.fit")));
        assertThat(stagedFit.errors()).isEmpty();
        final var fitPendingId = stagedFit.staged().getFirst().id();
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Reimport IT Site",
                        new Location(1.0, 2.0),
                        null,
                        null);
        final var committedDive = importService.commit(user, fitPendingId, commitRequest);
        final var fullDive = diveService.getDiveById(user, committedDive.id()).orElseThrow();
        final var profileId = fullDive.profiles().getFirst().id();

        // Before reimport: FIT carries no TTS at all (confirmed elsewhere for this device/format).
        assertThat(fullDive.summary().maxTimeToSurface()).isNull();

        final var preview =
                importService.previewReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        0,
                        fixture("suunto-eon-core-dive-1-deco.json"));
        // Neither FIT nor this Suunto JSON export carries notes/visibility/gasConsumption/named
        // buddies, so nothing should conflict here - real TTS-backfill use case is a clean commit.
        assertThat(preview.conflicts().hasAny()).isFalse();

        final var updated =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        preview.pendingImportId(),
                        new ReimportResolution(null, null, null, null, null));

        assertThat(updated.summary().maxTimeToSurface()).isEqualTo(Duration.ofSeconds(532));
        // The dive's identity/profile count is unchanged - reimport replaced, did not add.
        assertThat(updated.profiles()).hasSize(1);
        assertThat(updated.profiles().getFirst().id()).isEqualTo(profileId);
    }

    /**
     * A Shearwater native XML import (naive-UTC clock, corrected to the dive site's real zone at
     * commit time) reimported with the UDDF export of the same dive (also naive-UTC): the profiles
     * match apart from a whole-hour clock gap, so instead of rejecting it the diver is asked which
     * start time to keep. EXISTING keeps the corrected time; NEW adopts the file's raw clock.
     */
    @Test
    void reimportingAcrossAWholeHourClockOffsetAsksWhichTimeToKeep() throws IOException {
        final var user = createTestUser("reimport-tz-it@test.ch");
        final var staged =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2-native.xml")));
        final var pendingId = staged.staged().getFirst().id();
        // Male, Maldives - always UTC+5, no DST: the XML's raw "10:13:49" wall clock is corrected
        // to 05:13:49Z on commit.
        final var committedDive =
                importService.commit(
                        user,
                        pendingId,
                        new PendingImportCommitRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Male, Maldives",
                                new Location(4.1755, 73.5093),
                                null,
                                null));
        final var profileId =
                diveService
                        .getDiveById(user, committedDive.id())
                        .orElseThrow()
                        .profiles()
                        .getFirst()
                        .id();
        final var correctedStart = Instant.parse("2026-08-22T05:13:49Z");
        final var rawFileStart = Instant.parse("2026-08-22T10:13:49Z");

        final var preview =
                importService.previewReimportProfile(
                        user, committedDive.id(), profileId, 0, fixture("shearwater-perdix2.uddf"));
        final var clockOffset = Objects.requireNonNull(preview.conflicts().clockOffset());
        assertThat(clockOffset.existingStart()).isEqualTo(correctedStart);
        assertThat(clockOffset.reimportedStart()).isEqualTo(rawFileStart);
        assertThat(clockOffset.offsetMinutes()).isEqualTo(300);

        // No choice for the offset -> refused, same as any other unresolved conflict.
        assertThatThrownBy(
                        () ->
                                importService.commitReimportProfile(
                                        user,
                                        committedDive.id(),
                                        profileId,
                                        preview.pendingImportId(),
                                        new ReimportResolution(null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start time");

        // EXISTING: the reimported profile is re-aligned onto the dive's corrected clock.
        final var kept =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        preview.pendingImportId(),
                        new ReimportResolution(
                                null, null, null, null, ReimportResolution.Choice.EXISTING));
        assertThat(kept.profiles().getFirst().start()).isEqualTo(correctedStart);
    }

    @Test
    void reimportingAcrossAWholeHourClockOffsetCanAdoptTheUploadedFilesClock() throws IOException {
        final var user = createTestUser("reimport-tz-it-2@test.ch");
        final var staged =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2-native.xml")));
        final var committedDive =
                importService.commit(
                        user,
                        staged.staged().getFirst().id(),
                        new PendingImportCommitRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Male, Maldives 2",
                                new Location(4.1755, 73.5093),
                                null,
                                null));
        final var profileId =
                diveService
                        .getDiveById(user, committedDive.id())
                        .orElseThrow()
                        .profiles()
                        .getFirst()
                        .id();

        final var preview =
                importService.previewReimportProfile(
                        user, committedDive.id(), profileId, 0, fixture("shearwater-perdix2.uddf"));

        final var updated =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        preview.pendingImportId(),
                        new ReimportResolution(
                                null, null, null, null, ReimportResolution.Choice.NEW));
        assertThat(updated.profiles().getFirst().start())
                .isEqualTo(Instant.parse("2026-08-22T10:13:49Z"));
    }

    @Test
    void reimportingAnUnrelatedDiveIsRejectedRatherThanSilentlyReplacingTheWrongProfile()
            throws IOException {
        final var user = createTestUser("reimport-it-1@test.ch");
        final var stagedFit =
                importService.stageUpload(
                        user, List.of(fixture("suunto-eon-core-dive-1-deco.fit")));
        final var fitPendingId = stagedFit.staged().getFirst().id();
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Reimport IT Site 2",
                        new Location(3.0, 4.0),
                        null,
                        null);
        final var committedDive = importService.commit(user, fitPendingId, commitRequest);
        final var fullDive = diveService.getDiveById(user, committedDive.id()).orElseThrow();
        final var profileId = fullDive.profiles().getFirst().id();

        // dive-2-nodeco is a real, but genuinely different (shorter, shallower), dive.
        assertThatThrownBy(
                        () ->
                                importService.previewReimportProfile(
                                        user,
                                        committedDive.id(),
                                        profileId,
                                        0,
                                        fixture("suunto-eon-core-dive-2-nodeco.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doesn't look like the same dive")
                .hasMessageContaining("merge profiles");
    }
}
