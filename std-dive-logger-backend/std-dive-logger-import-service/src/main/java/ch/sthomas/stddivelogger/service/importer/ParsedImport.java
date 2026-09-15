package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.importfile.ImportLocator;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Result of parsing one dive out of a raw import source, before anything is persisted. Reader
 * services return this from their {@code parseOne(...)}-style methods; {@link ImportService} turns
 * it into a {@code PendingImportEntity} row (stage) and, later, into a real {@code Dive} (commit).
 *
 * @param locator where in its file this dive is - set by readers of multi-dive files
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
        PendingImportPayload payload,
        ImportLocator locator) {

    /** A dive that is the whole file. */
    public ParsedImport(
            final PendingImportSource source,
            final @Nullable String externalId,
            final @Nullable String filename,
            final @Nullable String diveIdentifierGuess,
            final @Nullable String siteNameGuess,
            final @Nullable Double latitudeGuess,
            final @Nullable Double longitudeGuess,
            final @Nullable String computerSerial,
            final @Nullable Instant startDate,
            final @Nullable Long durationSeconds,
            final @Nullable Double maxDepth,
            final PendingImportPayload payload) {
        this(
                source,
                externalId,
                filename,
                diveIdentifierGuess,
                siteNameGuess,
                latitudeGuess,
                longitudeGuess,
                computerSerial,
                startDate,
                durationSeconds,
                maxDepth,
                payload,
                ImportLocator.WHOLE_FILE);
    }

    /** The dive at this position of its file (UDDF entry, Subsurface dive). */
    public ParsedImport atEntry(final int entry) {
        return withLocator(ImportLocator.ofEntry(entry));
    }

    /** The dive with this id in its file (Shearwater Cloud database). */
    public ParsedImport withDiveId(final String id) {
        return withLocator(ImportLocator.ofId(id));
    }

    public ParsedImport withPayload(
            final PendingImportPayload newPayload, final @Nullable Instant newStart) {
        return new ParsedImport(
                source,
                externalId,
                filename,
                diveIdentifierGuess,
                siteNameGuess,
                latitudeGuess,
                longitudeGuess,
                computerSerial,
                newStart,
                durationSeconds,
                maxDepth,
                newPayload,
                locator);
    }

    private ParsedImport withLocator(final ImportLocator newLocator) {
        return new ParsedImport(
                source,
                externalId,
                filename,
                diveIdentifierGuess,
                siteNameGuess,
                latitudeGuess,
                longitudeGuess,
                computerSerial,
                startDate,
                durationSeconds,
                maxDepth,
                payload,
                newLocator);
    }
}
