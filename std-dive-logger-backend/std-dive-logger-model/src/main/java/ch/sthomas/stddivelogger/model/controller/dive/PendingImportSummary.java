package ch.sthomas.stddivelogger.model.controller.dive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Cheap, frontend-safe view of a staged import: everything needed to review and decide on
 * overrides, but never the parsed profile/measurement data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingImportSummary(
        long id,
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
        Instant createdAt,
        // The dive number guessed from the source file (e.g. UDDF's <divenumber>), and whether it
        // was fractional (a "+"/"-"-prefixed Shearwater bailout/CC companion marker) - both drive
        // the frontend's "attach to existing dive" preselection, since a fractional guess means
        // commit() will silently attach to that number regardless of whichever mode the UI shows.
        @Nullable Integer diveNumberGuess,
        boolean diveNumberFractional) {}
