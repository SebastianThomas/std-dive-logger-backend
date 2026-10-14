package ch.sthomas.stddivelogger.model.dive.profile;

import java.time.Duration;
import java.time.Instant;

/** A span of time, {@code start} before {@code end} - a section to cut, or a gap a cut left. */
public record TimeRange(Instant start, Instant end) {
    public TimeRange {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "A time range must start before it ends (" + start + " - " + end + ").");
        }
    }

    public boolean contains(final Instant t) {
        return !t.isBefore(start) && !t.isAfter(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public TimeRange shifted(final Duration offset) {
        return new TimeRange(start.plus(offset), end.plus(offset));
    }
}
