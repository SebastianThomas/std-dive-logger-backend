package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public record DiveSummary(
        Instant start,
        Instant end,
        double averageDepth,
        double maxDepth,
        @Nullable Duration surfaceIntervalBefore,
        Duration bottomTime) {}
