package ch.sthomas.stddivelogger.model.dive.stats;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsTimeSeriesPoint(
        Instant bucketStart,
        /** Only set for {@link StatsGranularity#PER_DIVE}, where each bucket is exactly one dive. */
        @Nullable Long diveId,
        /** Only set on entries in {@link StatsTimeSeries#breakdown()}, never on {@code points}. */
        @Nullable String category,
        long diveCount,
        Double avgRmvLiters,
        Double maxDepth,
        Double avgDepth,
        long totalDurationSeconds,
        long maxDurationSeconds,
        Double avgEndCns,
        Double avgTemperatureCelsius,
        Double avgVisibilityMeters,
        Double avgWeightKg) {}
