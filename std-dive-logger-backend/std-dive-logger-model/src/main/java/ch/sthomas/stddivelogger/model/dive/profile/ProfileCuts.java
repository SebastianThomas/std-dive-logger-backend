package ch.sthomas.stddivelogger.model.dive.profile;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * What a trim keeps of a profile: the samples inside {@code [trimStart, trimEnd]} and outside every
 * cut. A cut keeps the real clock - it leaves a gap from the last sample before it to the first
 * after it, which no longer counts as dive time. Shared by saved and staged profiles.
 */
public final class ProfileCuts {

    private ProfileCuts() {}

    public static boolean kept(
            final Instant t,
            final @Nullable Instant trimStart,
            final @Nullable Instant trimEnd,
            final List<TimeRange> cuts) {
        return (trimStart == null || !t.isBefore(trimStart))
                && (trimEnd == null || !t.isAfter(trimEnd))
                && cuts.stream().noneMatch(c -> c.contains(t));
    }

    /** The samples a trim keeps, in their original order. */
    public static <T> List<T> keep(
            final List<T> samples,
            final Function<T, Instant> time,
            final @Nullable Instant trimStart,
            final @Nullable Instant trimEnd,
            final List<TimeRange> cuts) {
        if (trimStart != null && trimEnd != null && !trimStart.isBefore(trimEnd)) {
            throw new IllegalArgumentException("Trim start must be before trim end.");
        }
        final var kept =
                samples.stream()
                        .filter(s -> kept(time.apply(s), trimStart, trimEnd, cuts))
                        .toList();
        if (kept.size() < 2) {
            throw new IllegalArgumentException(
                    "Trimming this range would leave fewer than 2 measurements on the profile.");
        }
        return kept;
    }

    /**
     * The gaps after a trim: each cut's that removed a sample (bridging the kept samples around it)
     * plus the existing ones still inside the kept samples, merged where they touch.
     *
     * @param times every sample time before the trim
     * @param keptTimes the surviving sample times, ascending
     */
    public static List<TimeRange> gaps(
            final List<Instant> times,
            final List<Instant> keptTimes,
            final List<TimeRange> cuts,
            final List<TimeRange> existing) {
        final var first = keptTimes.getFirst();
        final var last = keptTimes.getLast();
        final var all = new ArrayList<TimeRange>();
        for (final var cut : cuts) {
            if (times.stream().noneMatch(cut::contains)) {
                continue;
            }
            final var before =
                    keptTimes.stream().filter(t -> t.isBefore(cut.start())).reduce((a, b) -> b);
            final var after = keptTimes.stream().filter(t -> t.isAfter(cut.end())).findFirst();
            if (before.isPresent() && after.isPresent()) {
                all.add(new TimeRange(before.get(), after.get()));
            }
        }
        for (final var gap : existing) {
            final var start = gap.start().isBefore(first) ? first : gap.start();
            final var end = gap.end().isAfter(last) ? last : gap.end();
            if (start.isBefore(end)) {
                all.add(new TimeRange(start, end));
            }
        }
        return merge(all);
    }

    /** Sorted, with overlapping or touching ranges joined. */
    public static List<TimeRange> merge(final List<TimeRange> ranges) {
        final var sorted = ranges.stream().sorted(Comparator.comparing(TimeRange::start)).toList();
        final var merged = new ArrayList<TimeRange>();
        for (final var range : sorted) {
            if (!merged.isEmpty() && !range.start().isAfter(merged.getLast().end())) {
                final var previous = merged.removeLast();
                merged.add(
                        new TimeRange(
                                previous.start(),
                                range.end().isAfter(previous.end())
                                        ? range.end()
                                        : previous.end()));
            } else {
                merged.add(range);
            }
        }
        return List.copyOf(merged);
    }

    public static Duration total(final List<TimeRange> gaps) {
        return gaps.stream().map(TimeRange::duration).reduce(Duration.ZERO, Duration::plus);
    }
}
