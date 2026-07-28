package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

public record DiveSummary(
        Instant start,
        Instant end,
        double averageDepth,
        double maxDepth,
        @Nullable Duration surfaceIntervalBefore,
        Duration bottomTime) {}
