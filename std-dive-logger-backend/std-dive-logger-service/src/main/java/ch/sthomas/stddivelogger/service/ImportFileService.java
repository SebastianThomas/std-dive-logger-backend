package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.ImportFileDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.data.service.storage.ImportFileStore;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.entity.DiveImportFileEntity;
import ch.sthomas.stddivelogger.model.entity.ImportFileEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.importfile.DiveSourceFile;
import ch.sthomas.stddivelogger.model.importfile.ImportFileInfo;
import ch.sthomas.stddivelogger.model.importfile.ImportFileSettings;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Opt-in retention of uploaded dive files: storing, listing, downloading and deleting them. Bytes
 * on the local volume ({@link ImportFileStore}), one row per distinct file and account.
 */
@Service
public class ImportFileService {
    private static final Logger logger = LoggerFactory.getLogger(ImportFileService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    public record StoredFile(String filename, String contentType, byte[] bytes) {}

    private record SourceFileBuilder(
            ImportFileEntity file, Set<Long> profileIds, Set<ImportedDiveField> fields) {}

    private final ImportFileDataService importFileDataService;
    private final ImportFileStore importFileStore;
    private final UserDataService userDataService;
    private final DiveDataService diveDataService;

    public ImportFileService(
            final ImportFileDataService importFileDataService,
            final ImportFileStore importFileStore,
            final UserDataService userDataService,
            final DiveDataService diveDataService) {
        this.importFileDataService = importFileDataService;
        this.importFileStore = importFileStore;
        this.userDataService = userDataService;
        this.diveDataService = diveDataService;
    }

    public ImportFileSettings getSettings(final User user) {
        return new ImportFileSettings(
                userDataService.isKeepImportFiles(user.id()),
                importFileDataService.countByUser(user.id()),
                importFileDataService.totalBytesByUser(user.id()));
    }

    /** Turning it off keeps what is stored; {@link #deleteAll} removes that. */
    public ImportFileSettings setKeepImportFiles(final User user, final boolean keep) {
        userDataService.setKeepImportFiles(user.id(), keep);
        return getSettings(user);
    }

    public List<ImportFileInfo> list(final User user) {
        return importFileDataService.findByUser(user.id()).stream()
                .map(ImportFileEntity::toInfo)
                .toList();
    }

    /**
     * Keeps the upload when the account opted in and returns its row id, else null. A storage
     * failure is logged, not thrown - the import itself must still work.
     */
    public @Nullable Long storeIfKept(
            final User user,
            final PendingImportSource source,
            final @Nullable String filename,
            final @Nullable String contentType,
            final byte[] bytes,
            final int diveCount,
            final int profileCount,
            final @Nullable JsonNode metadata) {
        if (!userDataService.isKeepImportFiles(user.id())) {
            return null;
        }
        final var sha256 = sha256(bytes);
        final var path = user.id() + "/" + sha256;
        try {
            importFileStore.write(path, bytes);
            return importFileDataService
                    .saveOrReuse(
                            user.id(),
                            source,
                            filename,
                            contentType == null || contentType.isBlank()
                                    ? DEFAULT_CONTENT_TYPE
                                    : contentType,
                            bytes.length,
                            sha256,
                            path,
                            diveCount,
                            profileCount,
                            metadata)
                    .getId();
        } catch (final IOException | RuntimeException e) {
            logger.error("Could not keep uploaded file {} of user {}", filename, user.id(), e);
            return null;
        }
    }

    public ImportFileEntity getOwned(final User user, final long fileId) {
        return importFileDataService
                .findOwned(fileId, user.id())
                .orElseThrow(() -> new NoSuchElementException("No stored file " + fileId));
    }

    public byte[] read(final ImportFileEntity file) {
        try {
            return importFileStore.read(file.getStoragePath());
        } catch (final IOException e) {
            throw new UncheckedIOException("Stored file " + file.getId() + " could not be read", e);
        }
    }

    public StoredFile download(final User user, final long fileId) {
        final var file = getOwned(user, fileId);
        final var filename =
                Optional.ofNullable(file.getOriginalFilename())
                        .filter(n -> !n.isBlank())
                        .orElse("dive-file-" + file.getId());
        return new StoredFile(filename, file.getContentType(), read(file));
    }

    /** The dive's stored files, each with the profiles and dive-level values it provided. */
    public List<DiveSourceFile> listForDive(final User user, final long diveId) {
        if (!diveDataService.hasWriteAccess(user, Set.of(diveId))) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var byFile = new LinkedHashMap<Long, SourceFileBuilder>();
        for (final var link : importFileDataService.findProfileLinksOfDive(diveId)) {
            builder(byFile, link.getFile()).profileIds().add(link.getProfileId());
        }
        final var diveLinks = importFileDataService.findDiveLinks(diveId);
        final var fields =
                importFileDataService.findFieldsByDiveLink(
                        diveLinks.stream().map(DiveImportFileEntity::getId).toList());
        for (final var link : diveLinks) {
            final var builder = builder(byFile, link.getFile());
            fields.getOrDefault(link.getId(), List.of())
                    .forEach(f -> builder.fields().add(f.getField()));
        }
        return byFile.values().stream()
                .map(
                        b ->
                                new DiveSourceFile(
                                        b.file().toInfo(),
                                        List.copyOf(b.profileIds()),
                                        List.copyOf(b.fields())))
                .sorted(Comparator.comparing(f -> f.file().createdAt()))
                .toList();
    }

    private static SourceFileBuilder builder(
            final LinkedHashMap<Long, SourceFileBuilder> byFile, final ImportFileEntity file) {
        return byFile.computeIfAbsent(
                file.getId(),
                _ ->
                        new SourceFileBuilder(
                                file, new TreeSet<>(), EnumSet.noneOf(ImportedDiveField.class)));
    }

    public int deleteAll(final User user) {
        return deleteAllOfAccount(user.id());
    }

    /** Rows first, then bytes: a failed disk delete leaves a stray file, never a dangling row. */
    public int deleteAllOfAccount(final long userId) {
        final var paths = importFileDataService.deleteAllOfUser(userId);
        paths.forEach(this::deleteBytes);
        return paths.size();
    }

    /**
     * Deletes files no profile, dive or pending import refers to, untouched since {@code cutoff}.
     */
    public int sweepUnreferenced(final Instant cutoff) {
        final var orphans = importFileDataService.findUnreferencedSince(cutoff);
        for (final var file : orphans) {
            importFileDataService.delete(file);
            deleteBytes(file.getStoragePath());
        }
        if (!orphans.isEmpty()) {
            logger.info("Deleted {} stored import file(s) nothing refers to", orphans.size());
        }
        return orphans.size();
    }

    private void deleteBytes(final String path) {
        try {
            importFileStore.delete(path);
        } catch (final IOException | RuntimeException e) {
            logger.error("Could not delete stored import file {}", path, e);
        }
    }

    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
