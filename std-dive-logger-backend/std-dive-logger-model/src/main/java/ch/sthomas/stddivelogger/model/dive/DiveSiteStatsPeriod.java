package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** Global, anonymous dive activity at a site for one calendar-month bucket. */
public record DiveSiteStatsPeriod(
        Instant start,
        long diveCount,
        long distinctDivers,
        @Nullable Double averageMaxDepth,
        @Nullable Double deepestMaxDepth) {}
