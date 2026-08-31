package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/** The dive that holds a personal best, with enough to render a linked one-liner. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeRecordDive(
        long diveId,
        int diveNumber,
        @Nullable String identifier,
        @Nullable Instant diveStart,
        @Nullable Double maxDepth,
        @Nullable Duration bottomTime) {}
