package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

public record DiveSummary(
        Instant start,
        Instant end,
        // null for a manually-entered dive with no real depth-time profile to average - its
        // synthetic surface/max-depth/surface samples don't represent an actual dive shape, so
        // this is genuinely unknown unless the diver explicitly provides it (see
        // UpdateDiveBody.averageDepth). Always present for a dive with a real computer profile.
        @Nullable Double averageDepth,
        double maxDepth,
        @Nullable Duration surfaceIntervalBefore,
        Duration bottomTime,
        @Nullable Duration maxTimeToSurface) {}
