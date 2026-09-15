package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DiveImportFileFieldRepository;
import ch.sthomas.stddivelogger.data.repository.DiveImportFileRepository;
import ch.sthomas.stddivelogger.data.repository.DiveProfileImportFileRepository;
import ch.sthomas.stddivelogger.data.repository.ImportFileRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.entity.DiveImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.DiveImportFileFieldEntity;
import ch.sthomas.stddivelogger.model.entity.DiveProfileImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.ImportFileEntity;
import ch.sthomas.stddivelogger.model.importfile.ImportLocator;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Stored uploads and what was taken from them: profile links, dive links, dive field rows. */
@Service
public class ImportFileDataService {

    private final ImportFileRepository files;
    private final DiveProfileImportFileRepository profileLinks;
    private final DiveImportFileRepository diveLinks;
    private final DiveImportFileFieldRepository fields;
    private final NamedParameterJdbcTemplate jdbc;

    public ImportFileDataService(
            final ImportFileRepository files,
            final DiveProfileImportFileRepository profileLinks,
            final DiveImportFileRepository diveLinks,
            final DiveImportFileFieldRepository fields,
            final NamedParameterJdbcTemplate jdbc) {
        this.files = files;
        this.profileLinks = profileLinks;
        this.diveLinks = diveLinks;
        this.fields = fields;
        this.jdbc = jdbc;
    }

    /** The account's row for these bytes - the existing one when they were uploaded before. */
    @Transactional
    public ImportFileEntity saveOrReuse(
            final long userId,
            final PendingImportSource source,
            final @Nullable String filename,
            final String contentType,
            final long sizeBytes,
            final String sha256,
            final String storagePath,
            final int diveCount,
            final int profileCount,
            final @Nullable JsonNode metadata) {
        return files.findByUserIdAndSha256(userId, sha256)
                .map(f -> files.save(f.reuploaded(filename, diveCount, profileCount)))
                .orElseGet(
                        () ->
                                files.save(
                                        new ImportFileEntity(
                                                userId,
                                                source,
                                                filename,
                                                contentType,
                                                sizeBytes,
                                                sha256,
                                                storagePath,
                                                diveCount,
                                                profileCount,
                                                metadata)));
    }

    @Transactional(readOnly = true)
    public Optional<ImportFileEntity> findOwned(final long fileId, final long userId) {
        return files.findByIdAndUserId(fileId, userId);
    }

    @Transactional(readOnly = true)
    public List<ImportFileEntity> findByUser(final long userId) {
        return files.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countByUser(final long userId) {
        return files.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long totalBytesByUser(final long userId) {
        return files.sumSizeBytesByUserId(userId);
    }

    @Transactional
    public DiveProfileImportFileEntity linkProfile(
            final long profileId,
            final long fileId,
            final ImportLocator locator,
            final int parserVersion,
            final Instant rawActiveStart,
            final @Nullable Long siteId) {
        final var existing =
                profileLinks.findByProfileIdOrderByIdAsc(profileId).stream()
                        .filter(
                                l ->
                                        l.getFile().getId() == fileId
                                                && l.getLocator().equals(locator))
                        .findFirst();
        if (existing.isPresent()) {
            existing.get().stamp(parserVersion, rawActiveStart, siteId);
            return profileLinks.save(existing.get());
        }
        return profileLinks.save(
                new DiveProfileImportFileEntity(
                        profileId,
                        files.getReferenceById(fileId),
                        locator,
                        parserVersion,
                        rawActiveStart,
                        siteId));
    }

    /** Links the dive to the file and records (or updates) each value taken from it. */
    @Transactional
    public DiveImportFileEntity linkDive(
            final long diveId,
            final long fileId,
            final ImportLocator locator,
            final int parserVersion,
            final Map<ImportedDiveField, JsonNode> takenValues) {
        final var link =
                diveLinks.findByDiveIdOrderByIdAsc(diveId).stream()
                        .filter(
                                l ->
                                        l.getFile().getId() == fileId
                                                && l.getLocator().equals(locator))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        new DiveImportFileEntity(
                                                diveId,
                                                files.getReferenceById(fileId),
                                                locator,
                                                parserVersion));
        link.stampVersion(parserVersion);
        final var saved = diveLinks.save(link);
        takenValues.forEach((field, value) -> saveField(saved.getId(), field, value));
        return saved;
    }

    @Transactional
    public DiveImportFileFieldEntity saveField(
            final long diveLinkId, final ImportedDiveField field, final @Nullable JsonNode value) {
        final var row =
                fields.findByDiveImportFileId(diveLinkId).stream()
                        .filter(f -> f.getField() == field)
                        .findFirst()
                        .orElseGet(() -> new DiveImportFileFieldEntity(diveLinkId, field, value));
        row.setImportedValue(value);
        return fields.save(row);
    }

    @Transactional(readOnly = true)
    public List<DiveProfileImportFileEntity> findProfileLinks(final long profileId) {
        return profileLinks.findByProfileIdOrderByIdAsc(profileId);
    }

    @Transactional(readOnly = true)
    public List<DiveProfileImportFileEntity> findProfileLinksOfDive(final long diveId) {
        return profileLinks.findByDiveId(diveId);
    }

    @Transactional(readOnly = true)
    public List<DiveImportFileEntity> findDiveLinks(final long diveId) {
        return diveLinks.findByDiveIdOrderByIdAsc(diveId);
    }

    @Transactional(readOnly = true)
    public Optional<DiveImportFileEntity> findDiveLink(final long diveLinkId) {
        return diveLinks.findById(diveLinkId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<DiveImportFileFieldEntity>> findFieldsByDiveLink(
            final Collection<Long> diveLinkIds) {
        if (diveLinkIds.isEmpty()) {
            return Map.of();
        }
        return fields.findByDiveImportFileIdIn(diveLinkIds).stream()
                .collect(Collectors.groupingBy(DiveImportFileFieldEntity::getDiveImportFileId));
    }

    @Transactional
    public void saveProfileLinks(final Collection<DiveProfileImportFileEntity> links) {
        profileLinks.saveAll(links);
    }

    @Transactional
    public void saveDiveLink(final DiveImportFileEntity link) {
        diveLinks.save(link);
    }

    /**
     * Profiles with a link an importer update hasn't looked at yet, or whose dive moved to another
     * site since (the clock of timezone-less sources depends on it).
     */
    @Transactional(readOnly = true)
    public List<Long> findProfilesNeedingReprocessing(
            final ToIntFunction<PendingImportSource> currentVersion, final int limit) {
        return jdbc.queryForList(
                """
                SELECT DISTINCT l.fk_dive_profile_id
                FROM t_dive_profile_import_file l
                JOIN t_import_file f ON f.pk_import_file_id = l.fk_import_file_id
                JOIN t_dive_profiles p ON p.pk_dive_profile_id = l.fk_dive_profile_id
                JOIN t_dives d ON d.pk_dive_id = p.fk_dive_id
                WHERE l.parser_version < %s
                   OR l.processed_site_id IS DISTINCT FROM d.dive_site
                ORDER BY l.fk_dive_profile_id
                LIMIT :limit
                """
                        .formatted(versionCase(currentVersion)),
                Map.of("limit", limit),
                Long.class);
    }

    @Transactional(readOnly = true)
    public List<Long> findDiveLinksNeedingReprocessing(
            final ToIntFunction<PendingImportSource> currentVersion, final int limit) {
        return jdbc.queryForList(
                """
                SELECT l.pk_dive_import_file_id
                FROM t_dive_import_file l
                JOIN t_import_file f ON f.pk_import_file_id = l.fk_import_file_id
                WHERE l.parser_version < %s
                ORDER BY l.pk_dive_import_file_id
                LIMIT :limit
                """
                        .formatted(versionCase(currentVersion)),
                Map.of("limit", limit),
                Long.class);
    }

    /** Built from the enum and ints only - nothing user-supplied reaches the SQL text. */
    private static String versionCase(final ToIntFunction<PendingImportSource> currentVersion) {
        return Arrays.stream(PendingImportSource.values())
                .map(s -> "WHEN '" + s.name() + "' THEN " + currentVersion.applyAsInt(s))
                .collect(Collectors.joining(" ", "(CASE f.source ", " ELSE 0 END)"));
    }

    @Transactional(readOnly = true)
    public List<ImportFileEntity> findUnreferencedSince(final Instant cutoff) {
        return files.findUnreferencedSince(cutoff);
    }

    @Transactional
    public void delete(final ImportFileEntity file) {
        files.delete(file);
    }

    /** Deletes every stored-file row of the account (links cascade); returns their paths. */
    @Transactional
    public List<String> deleteAllOfUser(final long userId) {
        final var owned = files.findByUserIdOrderByCreatedAtDesc(userId);
        files.deleteAll(owned);
        return owned.stream()
                .map(ImportFileEntity::getStoragePath)
                .filter(Objects::nonNull)
                .toList();
    }
}
