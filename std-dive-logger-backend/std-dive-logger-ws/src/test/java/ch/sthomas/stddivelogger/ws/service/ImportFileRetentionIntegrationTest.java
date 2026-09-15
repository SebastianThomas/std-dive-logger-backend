package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.importfile.ImportFileScope;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictKind;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.ImportFileService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.service.importer.ImportService;
import ch.sthomas.stddivelogger.service.importer.reprocess.ImportReprocessService;

import jakarta.persistence.EntityManager;

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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Opt-in retention of uploaded dive files end to end: storing and linking at stage / commit /
 * refine, and re-processing - unchanged files change nothing, missing values are filled in, real
 * changes wait for the diver, alignment / trims / the diver's own edits survive.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class ImportFileRetentionIntegrationTest {
    private static final Path IMPORT_FILES_DIR = tempDir();
    private static final Location MALE = new Location(4.1755, 73.5093);
    private static final Location SOMEWHERE = new Location(1.0, 2.0);

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
        registry.add("ch.sthomas.stddivelogger.import-files.dir", IMPORT_FILES_DIR::toString);
    }

    @Autowired private ImportService importService;
    @Autowired private ImportReprocessService reprocessService;
    @Autowired private ImportFileService importFileService;
    @Autowired private DiveService diveService;
    @Autowired private UserService userService;
    @Autowired private UserDataService userDataService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("import-files-it");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MockMultipartFile fixture(final String filename) throws IOException {
        try (final var in =
                ImportFileRetentionIntegrationTest.class
                        .getClassLoader()
                        .getResourceAsStream(filename)) {
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/octet-stream",
                    Objects.requireNonNull(in).readAllBytes());
        }
    }

    private User user(final String email, final boolean keepFiles) {
        final var user = userRepository.save(new UserEntity(email, "hash", email)).toRecord();
        userDataService.setKeepImportFiles(user.id(), keepFiles);
        return user;
    }

    private long importAndCommit(
            final User user, final String fixture, final String siteName, final Location site)
            throws IOException {
        final var staged = importService.stageUpload(user, List.of(fixture(fixture)));
        assertThat(staged.errors()).isEmpty();
        return importService
                .commit(
                        user,
                        staged.staged().getFirst().id(),
                        new PendingImportCommitRequest(
                                null, null, null, null, null, null, siteName, site, null, null))
                .id();
    }

    private void refine(final User user, final long diveId, final String fixture)
            throws IOException {
        final var profileId = profileId(user, diveId);
        final var preview =
                importService.previewReimportProfile(user, diveId, profileId, 0, fixture(fixture));
        importService.commitReimportProfile(
                user,
                diveId,
                profileId,
                preview.pendingImportId(),
                new ReimportResolution(null, null, null, null, null));
    }

    private Dive dive(final User user, final long diveId) {
        entityManager.flush();
        entityManager.clear();
        final var dive = diveService.getDiveById(user, diveId).orElseThrow();
        // Detached again: in this one test transaction a later call would otherwise find the
        // measurement collections loaded here, and Hibernate undoes deletes of rows still in them.
        entityManager.clear();
        return dive;
    }

    private long profileId(final User user, final long diveId) {
        return dive(user, diveId).profiles().getFirst().id();
    }

    private List<DiveMeasurement> samples(final User user, final long diveId) {
        return Objects.requireNonNull(dive(user, diveId).profiles().getFirst().measurements())
                .stream()
                .map(DiveMeasurementWithId::measurement)
                .toList();
    }

    /** SQL behind Hibernate's back: flushed before, persistence context cleared after. */
    private void sql(final String statement, final Object... args) {
        entityManager.flush();
        jdbcTemplate.update(statement, args);
        entityManager.clear();
    }

    /** As if the importer of the profile's files had been updated since. */
    private ImportReprocessService.RunResult reprocessProfile(final long profileId) {
        sql(
                "UPDATE t_dive_profile_import_file SET parser_version = 0 WHERE fk_dive_profile_id = ?",
                profileId);
        return reprocess();
    }

    private ImportReprocessService.RunResult reprocess() {
        final var result = reprocessService.reprocessPending();
        entityManager.flush();
        entityManager.clear();
        assertThat(result.failed()).isZero();
        return result;
    }

    @Test
    void nothingIsKeptForAnAccountThatDidNotOptIn() throws IOException {
        final var user = user("files-it-0@test.ch", false);

        final var staged =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2.uddf")));

        assertThat(staged.staged()).hasSize(1);
        assertThat(importFileService.list(user)).isEmpty();
    }

    @Test
    void anUploadIsKeptOnceAndLinkedToItsProfileAndTheDiveValuesItProvided() throws IOException {
        final var user = user("files-it-1@test.ch", true);
        final var first =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2.uddf")));
        final var again =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2.uddf")));

        final var files = importFileService.list(user);
        assertThat(files).hasSize(1);
        final var file = files.getFirst();
        assertThat(file.source()).isEqualTo(PendingImportSource.UDDF_SHEARWATER);
        assertThat(file.scope()).isEqualTo(ImportFileScope.SINGLE_PROFILE);
        assertThat(importFileService.download(user, file.id()).bytes())
                .isEqualTo(fixture("shearwater-perdix2.uddf").getBytes());

        importService.discard(user, again.staged().getFirst().id());
        final var diveId =
                importService
                        .commit(
                                user,
                                first.staged().getFirst().id(),
                                new PendingImportCommitRequest(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "Male files 1",
                                        MALE,
                                        null,
                                        null))
                        .id();

        final var profileId = profileId(user, diveId);
        final var sources = importFileService.listForDive(user, diveId);
        assertThat(sources)
                .singleElement()
                .satisfies(
                        source -> {
                            assertThat(source.file().id()).isEqualTo(file.id());
                            assertThat(source.profileIds()).containsExactly(profileId);
                            assertThat(source.fields())
                                    .contains(
                                            ImportedDiveField.NOTES,
                                            ImportedDiveField.VISIBILITY,
                                            ImportedDiveField.DIVE_IDENTIFIER);
                        });
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT import_files_complete FROM t_dive_profile_history"
                                        + " WHERE fk_dive_profile_id = ?",
                                Boolean.class,
                                profileId))
                .isTrue();
    }

    @Test
    void reprocessingUnchangedFilesChangesNothingEvenAfterARefineMergedTwoOfThem()
            throws IOException {
        final var user = user("files-it-2@test.ch", true);
        final var diveId = importAndCommit(user, "shearwater-perdix2.uddf", "Male files 2", MALE);
        refine(user, diveId, "shearwater-perdix2-native.xml");
        final var profileId = profileId(user, diveId);
        assertThat(importFileService.listForDive(user, diveId)).hasSize(2);
        final var before = samples(user, diveId);
        sql("UPDATE t_dive_import_file SET parser_version = 0 WHERE fk_dive_id = ?", diveId);

        final var result = reprocessProfile(profileId);

        assertThat(result.applied()).isZero();
        assertThat(result.conflicts()).isZero();
        assertThat(samples(user, diveId)).isEqualTo(before);
        assertThat(reprocessService.listOpen(user)).isEmpty();
    }

    @Test
    void valuesMissingFromTheStoredProfileAreFilledInWithoutAsking() throws IOException {
        final var user = user("files-it-3@test.ch", true);
        final var diveId =
                importAndCommit(user, "suunto-eon-core-dive-1-deco.json", "Files 3", SOMEWHERE);
        final var profileId = profileId(user, diveId);
        final var before = samples(user, diveId);
        assertThat(before).anyMatch(m -> m.timeToSurface() != null);
        sql(
                "UPDATE t_dive_measurements SET time_to_surface_seconds = NULL"
                        + " WHERE fk_dive_profile_id = ?",
                profileId);

        final var result = reprocessProfile(profileId);

        assertThat(result.applied()).isEqualTo(1);
        assertThat(reprocessService.listOpen(user)).isEmpty();
        assertThat(samples(user, diveId)).isEqualTo(before);
    }

    @Test
    void aRealChangeWaitsForTheDiverAndIsOnlyAppliedOnRequest() throws IOException {
        final var user = user("files-it-4@test.ch", true);
        final var diveId =
                importAndCommit(user, "suunto-eon-core-dive-1-deco.json", "Files 4", SOMEWHERE);
        final var profileId = profileId(user, diveId);
        final var before = samples(user, diveId);
        sql(
                "UPDATE t_dive_measurements SET depth = depth + 3 WHERE pk_dive_measurement_id ="
                        + " (SELECT pk_dive_measurement_id FROM t_dive_measurements"
                        + " WHERE fk_dive_profile_id = ? AND depth > 5 ORDER BY elapsed LIMIT 1)",
                profileId);
        final var edited = samples(user, diveId);

        reprocessProfile(profileId);

        assertThat(samples(user, diveId)).isEqualTo(edited);
        final var open = reprocessService.listOpen(user);
        assertThat(open)
                .singleElement()
                .satisfies(
                        conflict -> {
                            assertThat(conflict.kind()).isEqualTo(ReprocessConflictKind.PROFILE);
                            assertThat(conflict.profileId()).isEqualTo(profileId);
                            assertThat(conflict.summary()).isEqualTo("Depth changes on 1 sample");
                        });

        assertThat(reprocessService.apply(user, open.getFirst().id())).isEmpty();
        assertThat(samples(user, diveId)).isEqualTo(before);
    }

    @Test
    void keepingTheCurrentProfileIsNotAskedAgainForTheSameImporter() throws IOException {
        final var user = user("files-it-5@test.ch", true);
        final var diveId =
                importAndCommit(user, "suunto-eon-core-dive-1-deco.json", "Files 5", SOMEWHERE);
        final var profileId = profileId(user, diveId);
        sql(
                "UPDATE t_dive_measurements SET depth = depth + 3 WHERE pk_dive_measurement_id ="
                        + " (SELECT pk_dive_measurement_id FROM t_dive_measurements"
                        + " WHERE fk_dive_profile_id = ? AND depth > 5 ORDER BY elapsed LIMIT 1)",
                profileId);
        final var edited = samples(user, diveId);
        reprocessProfile(profileId);

        assertThat(reprocessService.keep(user, reprocessService.listOpen(user).getFirst().id()))
                .isEmpty();
        reprocess();

        assertThat(reprocessService.listOpen(user)).isEmpty();
        assertThat(samples(user, diveId)).isEqualTo(edited);
    }

    @Test
    void aDiveMovedToAnotherTimezoneMovesTheShearwaterClockOnceApproved() throws IOException {
        final var user = user("files-it-6@test.ch", true);
        final var diveId = importAndCommit(user, "shearwater-perdix2.uddf", "Male files 6", MALE);
        // Male is UTC+5: the raw "10:13:49" wall clock reads as 05:13:49Z there.
        assertThat(dive(user, diveId).profiles().getFirst().start())
                .isEqualTo(Instant.parse("2026-08-22T05:13:49Z"));
        final var zurich =
                diveService.getOrCreateDiveSite("Zurich files 6", new Location(47.3769, 8.5417));
        sql("UPDATE t_dives SET dive_site = ? WHERE pk_dive_id = ?", zurich.id(), diveId);

        reprocess();

        final var open = reprocessService.listOpen(user);
        assertThat(open)
                .singleElement()
                .satisfies(
                        conflict ->
                                assertThat(conflict.summary()).isEqualTo("Clock moves 3 h later"));
        assertThat(dive(user, diveId).profiles().getFirst().start())
                .isEqualTo(Instant.parse("2026-08-22T05:13:49Z"));

        reprocessService.apply(user, open.getFirst().id());

        // Zurich is UTC+2 in August: the same wall clock is 08:13:49Z.
        assertThat(dive(user, diveId).profiles().getFirst().start())
                .isEqualTo(Instant.parse("2026-08-22T08:13:49Z"));
    }

    @Test
    void theDiversTrimAndAlignmentSurviveReprocessing() throws IOException {
        final var user = user("files-it-7@test.ch", true);
        final var diveId =
                importAndCommit(user, "suunto-eon-core-dive-1-deco.json", "Files 7", SOMEWHERE);
        final var profile = dive(user, diveId).profiles().getFirst();
        diveService.trimProfile(user, diveId, profile.id(), null, profile.end().minusSeconds(60));
        final var trimmed = dive(user, diveId).profiles().getFirst();
        diveService.alignProfilesManualToTime(
                user, Set.of(profile.id()), diveId, trimmed.start().plus(Duration.ofMinutes(10)));
        final var before = samples(user, diveId);

        final var result = reprocessProfile(profile.id());

        assertThat(result.applied()).isZero();
        assertThat(reprocessService.listOpen(user)).isEmpty();
        assertThat(samples(user, diveId)).isEqualTo(before);
    }

    @Test
    void aDiveValueTheDiverEditedStaysWhileOneOnlyTheFileChangedIsAsked() throws IOException {
        final var user = user("files-it-8@test.ch", true);
        final var diveId = importAndCommit(user, "shearwater-perdix2.uddf", "Male files 8", MALE);
        final var linkId =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                "SELECT pk_dive_import_file_id FROM t_dive_import_file WHERE fk_dive_id = ?",
                                Long.class,
                                diveId));
        // As if an older importer had read 4 m of visibility, still what the dive shows ...
        sql(
                "UPDATE t_dive_import_file_field SET imported_value ="
                        + " '{\"meters\":4.0,\"description\":\"\"}'::jsonb"
                        + " WHERE fk_dive_import_file_id = ? AND field = 'VISIBILITY'",
                linkId);
        diveService.applyReimportResolution(
                user, diveId, null, new Visibility(4.0, "", null), null, null);
        // ... and other notes, which the diver has rewritten since.
        sql(
                "UPDATE t_dive_import_file_field SET imported_value = '\"Old import\"'::jsonb"
                        + " WHERE fk_dive_import_file_id = ? AND field = 'NOTES'",
                linkId);
        diveService.applyReimportResolution(user, diveId, "My own notes", null, null, null);
        sql(
                "UPDATE t_dive_import_file SET parser_version = 0 WHERE pk_dive_import_file_id = ?",
                linkId);

        reprocess();

        final var open = reprocessService.listOpen(user);
        assertThat(open)
                .singleElement()
                .satisfies(
                        conflict -> {
                            assertThat(conflict.kind()).isEqualTo(ReprocessConflictKind.DIVE_FIELD);
                            assertThat(conflict.field()).isEqualTo(ImportedDiveField.VISIBILITY);
                            assertThat(conflict.summary()).startsWith("Visibility: 4 m → 6 m");
                        });
        assertThat(dive(user, diveId).notes()).isEqualTo("My own notes");

        reprocessService.apply(user, open.getFirst().id());

        assertThat(Objects.requireNonNull(dive(user, diveId).visibility()).meters()).isEqualTo(6.0);
    }

    @Test
    void aDiveValueTheFileHasButTheDiveLacksIsFilledInWithoutAsking() throws IOException {
        final var user = user("files-it-12@test.ch", true);
        final var diveId = importAndCommit(user, "shearwater-perdix2.uddf", "Male files 12", MALE);
        final var notes = dive(user, diveId).notes();
        assertThat(notes).isNotBlank();
        final var linkId =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                "SELECT pk_dive_import_file_id FROM t_dive_import_file WHERE fk_dive_id = ?",
                                Long.class,
                                diveId));
        // As if an older importer had read no notes at all.
        sql(
                "DELETE FROM t_dive_import_file_field"
                        + " WHERE fk_dive_import_file_id = ? AND field = 'NOTES'",
                linkId);
        diveService.applyReimportResolution(user, diveId, "", null, null, null);
        sql(
                "UPDATE t_dive_import_file SET parser_version = 0 WHERE pk_dive_import_file_id = ?",
                linkId);

        reprocess();

        assertThat(reprocessService.listOpen(user)).isEmpty();
        assertThat(dive(user, diveId).notes()).isEqualTo(notes);
        assertThat(importFileService.listForDive(user, diveId).getFirst().fields())
                .contains(ImportedDiveField.NOTES);
    }

    @Test
    void aProfileCanBeRefinedFromAStoredFileWithoutUploadingItAgain() throws IOException {
        final var user = user("files-it-9@test.ch", true);
        final var diveId = importAndCommit(user, "shearwater-perdix2.uddf", "Male files 9", MALE);
        final var profileId = profileId(user, diveId);
        final var fileId = importFileService.list(user).getFirst().id();
        final var before = samples(user, diveId);

        final var preview =
                importService.previewReimportProfileFromStoredFile(user, diveId, profileId, fileId);
        assertThat(preview.conflicts().hasAny()).isFalse();
        importService.commitReimportProfile(
                user,
                diveId,
                profileId,
                preview.pendingImportId(),
                new ReimportResolution(null, null, null, null, null));

        assertThat(samples(user, diveId)).hasSameSizeAs(before);
        assertThat(importFileService.listForDive(user, diveId)).hasSize(1);
    }

    @Test
    void deletingTheAccountDeletesItsKeptFiles() throws IOException {
        final var user = user("files-it-10@test.ch", true);
        importService.stageUpload(user, List.of(fixture("suunto-eon-core-dive-2-nodeco.json")));
        entityManager.flush();
        final var storagePath =
                Objects.requireNonNull(
                        jdbcTemplate.queryForObject(
                                "SELECT storage_path FROM t_import_file WHERE fk_user_id = ?",
                                String.class,
                                user.id()));
        assertThat(IMPORT_FILES_DIR.resolve(storagePath)).exists();

        userService.deleteUser(user);
        entityManager.flush();

        assertThat(IMPORT_FILES_DIR.resolve(storagePath)).doesNotExist();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM t_import_file WHERE fk_user_id = ?",
                                Long.class,
                                user.id()))
                .isZero();
    }

    @Test
    void aFileNothingRefersToIsSweptOnceItsGracePeriodIsOver() throws IOException {
        final var user = user("files-it-11@test.ch", true);
        final var staged =
                importService.stageUpload(
                        user, List.of(fixture("suunto-eon-core-dive-2-nodeco.json")));

        importFileService.sweepUnreferenced(Instant.now().plusSeconds(60));
        assertThat(importFileService.list(user)).as("its pending import refers to it").hasSize(1);

        importService.discard(user, staged.staged().getFirst().id());
        entityManager.flush();
        importFileService.sweepUnreferenced(Instant.now().minus(Duration.ofHours(1)));
        assertThat(importFileService.list(user)).as("within the grace period").hasSize(1);

        importFileService.sweepUnreferenced(Instant.now().plusSeconds(60));
        assertThat(importFileService.list(user)).isEmpty();
    }
}
