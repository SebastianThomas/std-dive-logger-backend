package ch.sthomas.stddivelogger.model.dive.stats;

import java.time.Duration;
import java.time.Instant;

public record DiveLength(Instant start, Instant end, Duration duration) {
    public DiveLength {
        final var d = Duration.between(start, end);
        if (!d.minus(duration).isZero()) {
            throw new IllegalArgumentException("Duration must be Duration.between(start, end)");
        }
        if (duration.compareTo(Duration.ZERO) <= 0) {
            throw new IllegalArgumentException("Duration cannot be greater than zero");
        }
    }

    public DiveLength(Instant start, Instant end) {
        this(start, end, Duration.between(start, end));
    }
}
