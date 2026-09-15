package ch.sthomas.stddivelogger.model.importfile;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * A re-processing result waiting for the diver. Values are only included for {@link
 * ReprocessConflictKind#DIVE_FIELD}; a profile proposal is summarized, not sent.
 */
public record ReprocessConflict(
        long id,
        long diveId,
        int diveNumber,
        @Nullable String diveIdentifier,
        @Nullable Long profileId,
        ReprocessConflictKind kind,
        @Nullable ImportedDiveField field,
        String summary,
        @Nullable JsonNode currentValue,
        @Nullable JsonNode proposedValue,
        Instant createdAt) {}
