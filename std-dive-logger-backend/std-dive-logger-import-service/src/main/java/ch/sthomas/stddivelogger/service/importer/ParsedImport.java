package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Result of parsing one dive out of a raw import source, before anything is persisted. Reader
 * services return this from their {@code parseOne(...)}-style methods; {@link ImportService} turns
 * it into a {@code PendingImportEntity} row (stage) and, later, into a real {@code Dive} (commit).
 */
public record ParsedImport(
        PendingImportSource source,
        @Nullable String externalId,
        @Nullable String filename,
        @Nullable String diveIdentifierGuess,
        @Nullable String siteNameGuess,
        @Nullable Double latitudeGuess,
        @Nullable Double longitudeGuess,
        @Nullable String computerSerial,
        @Nullable Instant startDate,
        @Nullable Long durationSeconds,
        @Nullable Double maxDepth,
        PendingImportPayload payload) {}
