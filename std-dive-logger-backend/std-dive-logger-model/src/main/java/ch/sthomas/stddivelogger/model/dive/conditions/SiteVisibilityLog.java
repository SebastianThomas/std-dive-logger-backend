package ch.sthomas.stddivelogger.model.dive.conditions;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One dive's visibility reading at a dive site, for the per-site visibility scatter view. At least
 * one of {@code meters} / {@code feeling} is non-null (the query filters out dives with neither).
 */
public record SiteVisibilityLog(
        long diveId,
        int diveNumber,
        String diveIdentifier,
        Instant date,
        @Nullable Double meters,
        @Nullable VisibilityFeeling feeling) {}
