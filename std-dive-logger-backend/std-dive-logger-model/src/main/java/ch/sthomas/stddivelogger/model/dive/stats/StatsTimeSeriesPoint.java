package ch.sthomas.stddivelogger.model.dive.stats;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsTimeSeriesPoint(
        Instant bucketStart,
        /**
         * Only set for {@link StatsGranularity#PER_DIVE}, where each bucket is exactly one dive.
         */
        @Nullable Long diveId,
        /** Only set on entries in {@link StatsTimeSeries#breakdown()}, never on {@code points}. */
        @Nullable String category,
        long diveCount,
        @Nullable Double avgRmvLiters,
        @Nullable Double maxDepth,
        @Nullable Double avgDepth,
        long totalDurationSeconds,
        long maxDurationSeconds,
        @Nullable Double avgEndCns,
        @Nullable Double avgTemperatureCelsius,
        @Nullable Double avgVisibilityMeters,
        @Nullable Double avgWeightKg,
        // Per-dive max TTS, then averaged/maxed across the dives in this bucket - not an
        // average/max of raw per-sample TTS readings.
        @Nullable Double avgMaxTimeToSurfaceSeconds,
        @Nullable Double maxMaxTimeToSurfaceSeconds) {}
