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
        Instant createdAt) {}
