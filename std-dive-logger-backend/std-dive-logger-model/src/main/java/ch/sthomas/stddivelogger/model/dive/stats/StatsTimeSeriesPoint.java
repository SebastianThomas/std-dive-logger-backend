package ch.sthomas.stddivelogger.model.dive.stats;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsTimeSeriesPoint(
        Instant bucketStart,
        /** Only set for {@link StatsGranularity#PER_DIVE}, where each bucket is exactly one dive. */
        Long diveId,
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
