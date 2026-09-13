package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
@org.junit.jupiter.api.Tag("slow")
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
    @Autowired private JdbcTemplate jdbcTemplate;
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
     * Refining a Shearwater import with the same dive's other Shearwater export (UDDF first, then
     * the native XML with the better data) must not move the profile: both files carry the same
     * timezone-less wall clock, and the reimport is corrected to the dive site's zone exactly like
     * the original commit was.
     */
    @Test
    void refiningAShearwaterUddfImportWithTheNativeXmlKeepsTheCorrectedStart() throws IOException {
        final var user = createTestUser("reimport-tz-it-0@test.ch");
        final var staged =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2.uddf")));
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
                                "Male, Maldives 0",
                                new Location(4.1755, 73.5093),
                                null,
                                null));
        final var profile =
                diveService
                        .getDiveById(user, committedDive.id())
                        .orElseThrow()
                        .profiles()
                        .getFirst();
        // Male, Maldives - always UTC+5, no DST: the raw "10:13:49" wall clock is 05:13:49Z.
        final var correctedStart = Instant.parse("2026-08-22T05:13:49Z");
        assertThat(profile.start()).isEqualTo(correctedStart);

        final var preview =
                importService.previewReimportProfile(
                        user,
                        committedDive.id(),
                        profile.id(),
                        0,
                        fixture("shearwater-perdix2-native.xml"));
        assertThat(preview.conflicts().clockOffset()).isNull();

        final var refined =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profile.id(),
                        preview.pendingImportId(),
                        new ReimportResolution(null, null, null, null, null));
        assertThat(refined.profiles().getFirst().start()).isEqualTo(correctedStart);
        // The refine replaces the rows: no copy of the UDDF samples may survive next to the XML's.
        assertThat(
                        jdbcTemplate.queryForObject(
                                """
                                SELECT count(*) - count(DISTINCT elapsed) FROM t_dive_measurements
                                WHERE fk_dive_profile_id = ?
                                """,
                                Long.class,
                                profile.id()))
                .isZero();
    }

    /**
     * Refining merges instead of replacing, so which of the two Shearwater exports came first
     * doesn't matter: UDDF-then-XML and XML-then-UDDF end up with identical measurements, carrying
     * everything either file has (e.g. the XML's real per-sample TTS).
     */
    @Test
    void refiningWithTheOtherShearwaterExportGivesTheSameProfileInEitherOrder() throws IOException {
        final var uddfFirst =
                importAndRefine(
                        "reimport-order-1@test.ch",
                        "shearwater-perdix2.uddf",
                        "shearwater-perdix2-native.xml",
                        "Male, Maldives order 1");
        final var xmlFirst =
                importAndRefine(
                        "reimport-order-2@test.ch",
                        "shearwater-perdix2-native.xml",
                        "shearwater-perdix2.uddf",
                        "Male, Maldives order 2");

        assertThat(uddfFirst).isEqualTo(xmlFirst);
        assertThat(uddfFirst).anyMatch(m -> m.timeToSurface() != null);
        assertThat(uddfFirst).extracting(DiveMeasurement::time).doesNotHaveDuplicates();
    }

    private List<DiveMeasurement> importAndRefine(
            final String email, final String first, final String second, final String siteName)
            throws IOException {
        // Own display name per user - t_users.name is unique, and this test creates two.
        final var user = userRepository.save(new UserEntity(email, "hash", email)).toRecord();
        final var staged = importService.stageUpload(user, List.of(fixture(first)));
        final var dive =
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
                                siteName,
                                new Location(4.1755, 73.5093),
                                null,
                                null));
        final var profileId =
                diveService.getDiveById(user, dive.id()).orElseThrow().profiles().getFirst().id();
        final var preview =
                importService.previewReimportProfile(
                        user, dive.id(), profileId, 0, fixture(second));
        importService.commitReimportProfile(
                user,
                dive.id(),
                profileId,
                preview.pendingImportId(),
                new ReimportResolution(null, null, null, null, null));
        return Objects.requireNonNull(
                        diveService
                                .getDiveById(user, dive.id())
                                .orElseThrow()
                                .profiles()
                                .getFirst()
                                .measurements())
                .stream()
                .map(DiveMeasurementWithId::measurement)
                .toList();
    }

    /**
     * A dive whose Shearwater profile still sits on the raw, uncorrected clock (imported while the
     * timezone correction was skipped) refined with the same dive's export: the corrected reimport
     * matches apart from a whole-hour gap, so instead of rejecting it the diver is asked which
     * start time to keep. EXISTING keeps the dive's current clock; NEW adopts the corrected one.
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

        // Puts the dive back on the raw file clock, like a dive imported before the correction.
        diveService.setDiveStartTime(user, committedDive.id(), rawFileStart);
        final var preview =
                importService.previewReimportProfile(
                        user, committedDive.id(), profileId, 0, fixture("shearwater-perdix2.uddf"));
        final var clockOffset = Objects.requireNonNull(preview.conflicts().clockOffset());
        assertThat(clockOffset.existingStart()).isEqualTo(rawFileStart);
        assertThat(clockOffset.reimportedStart()).isEqualTo(correctedStart);
        assertThat(Math.abs(clockOffset.offsetMinutes())).isEqualTo(300);

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

        // EXISTING: the reimported profile is re-aligned onto the dive's current clock.
        final var kept =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        preview.pendingImportId(),
                        new ReimportResolution(
                                null, null, null, null, ReimportResolution.Choice.EXISTING));
        assertThat(kept.profiles().getFirst().start()).isEqualTo(rawFileStart);
    }

    @Test
    void reimportingAcrossAWholeHourClockOffsetCanAdoptTheCorrectedClock() throws IOException {
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

        diveService.setDiveStartTime(
                user, committedDive.id(), Instant.parse("2026-08-22T10:13:49Z"));
        final var preview =
                importService.previewReimportProfile(
                        user, committedDive.id(), profileId, 0, fixture("shearwater-perdix2.uddf"));

        // NEW: how a dive stuck on the raw clock gets fixed - it adopts the corrected start.
        final var updated =
                importService.commitReimportProfile(
                        user,
                        committedDive.id(),
                        profileId,
                        preview.pendingImportId(),
                        new ReimportResolution(
                                null, null, null, null, ReimportResolution.Choice.NEW));
        assertThat(updated.profiles().getFirst().start())
                .isEqualTo(Instant.parse("2026-08-22T05:13:49Z"));
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
