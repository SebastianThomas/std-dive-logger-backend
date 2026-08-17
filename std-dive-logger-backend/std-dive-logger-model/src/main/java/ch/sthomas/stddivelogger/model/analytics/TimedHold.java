package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shared "held until the next discrete event" timeline helper - a value (mode, gas mix, setpoint,
 * ...) only changes at a specific sample and holds until the next one, so building a sorted
 * timeline of every reported change and binary-searching "what was in effect at time T" is the
 * correct way to reconstruct it, rather than interpolating or taking the nearest sample. Extracted
 * out of {@link DiveGasCalculator} so other dive-wide-timeline calculations (e.g. cylinder
 * consumption's bailout-window detection) can reuse the exact same logic instead of reimplementing
 * it.
 */
public final class TimedHold {

    private TimedHold() {}

    public record TimedValue<T>(Instant time, T value) {}

    /**
     * Every profile's reported values for {@code extractor}, across all of a dive's profiles
     * together, sorted ascending by time.
     */
    public static <T> List<TimedValue<T>> timeline(
            final List<DiveProfile> profiles,
            final Function<DiveMeasurementWithId, @Nullable T> extractor) {
        final var result = new ArrayList<TimedValue<T>>();
        for (final var profile : profiles) {
            final var measurements = profile.measurements();
            if (measurements == null) {
                continue;
            }
            for (final var m : measurements) {
                final var value = extractor.apply(m);
                if (value != null) {
                    result.add(new TimedValue<>(m.measurement().time(), value));
                }
            }
        }
        result.sort(Comparator.comparing(TimedValue::time));
        return result;
    }

    /**
     * The last value at-or-before {@code time} in a list sorted ascending by time - a value only
     * changes at a discrete event and holds until the next one, so "nearest" would be wrong
     * whenever the *next* sample happens to be closer in time than the one actually in effect right
     * now. Falls back to the very first sample when {@code time} is before every recorded sample,
     * rather than returning nothing for the start of a profile.
     */
    public static <T> Optional<TimedValue<T>> heldAt(
            final List<TimedValue<T>> sorted, final Instant time) {
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        var lo = 0;
        var hi = sorted.size();
        while (lo < hi) {
            final var mid = (lo + hi) / 2;
            if (sorted.get(mid).time().isAfter(time)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return Optional.of(lo > 0 ? sorted.get(lo - 1) : sorted.getFirst());
    }
}
