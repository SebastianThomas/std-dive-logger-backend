package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** Global, anonymous aggregates for a dive site, shared with the suggestion engine. */
public record DiveSiteStats(
        @Nullable Instant computedAt,
        long totalDives,
        long distinctDivers,
        long recentDives30d,
        long recentDistinctDivers30d,
        @Nullable Double averageVisibilityMeters,
        long visibilitySampleSize,
        @Nullable Double averageMaxDepth,
        @Nullable Double shallowestMaxDepth,
        @Nullable Double deepestMaxDepth,
        long highlightedDives,
        List<DiveSiteStatsPeriod> monthlyActivity) {}
