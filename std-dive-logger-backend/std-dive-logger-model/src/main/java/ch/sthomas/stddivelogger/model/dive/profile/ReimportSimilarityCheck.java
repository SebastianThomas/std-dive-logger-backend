package ch.sthomas.stddivelogger.model.dive.profile;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Guards {@code DiveDataService.reimportProfileMeasurements} against replacing a profile with
 * measurements from what's actually a different dive - reimport is meant for "the same recording,
 * re-parsed" (e.g. a richer export of the same file), not for attaching a second computer's
 * recording of the same dive event (that's "merge profiles"/{@code addProfile}). Every tolerance
 * here is deliberately generous - it only needs to catch a clearly different dive, not nitpick
 * cross-format precision gaps (already characterized between Suunto FIT/JSON: start within ~1s, max
 * depth within ~0.5m, sample counts differing by a handful).
 */
public final class ReimportSimilarityCheck {
    private ReimportSimilarityCheck() {}

    private static final Duration START_TOLERANCE = Duration.ofMinutes(2);
    private static final Duration MIN_DURATION_TOLERANCE = Duration.ofMinutes(2);
    private static final double DURATION_TOLERANCE_FRACTION = 0.10;
    private static final double MAX_DEPTH_TOLERANCE_METERS = 2.0;
    private static final double CURVE_TOLERANCE_METERS = 4.0;
    private static final double[] CURVE_SAMPLE_FRACTIONS = {
        0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9
    };

    /**
     * Returns a human-readable mismatch reason if the two profiles don't look like the same dive,
     * or empty if they do (reimport may proceed).
     */
    public static Optional<String> checkSameDive(
            final Instant existingStart,
            final Instant existingEnd,
            final List<DiveMeasurement> existingMeasurements,
            final Instant newStart,
            final Instant newEnd,
            final List<DiveMeasurement> newMeasurements) {
        final var startDiff = Duration.between(existingStart, newStart).abs();
        if (startDiff.compareTo(START_TOLERANCE) > 0) {
            return Optional.of(
                    "start time differs by "
                            + startDiff.toMinutes()
                            + " min (existing "
                            + existingStart
                            + ", uploaded "
                            + newStart
                            + ")");
        }

        final var existingDuration = Duration.between(existingStart, existingEnd);
        final var newDuration = Duration.between(newStart, newEnd);
        final var durationTolerance =
                maxDuration(
                        MIN_DURATION_TOLERANCE,
                        Duration.ofSeconds(
                                Math.round(
                                        existingDuration.toSeconds()
                                                * DURATION_TOLERANCE_FRACTION)));
        final var durationDiff = newDuration.minus(existingDuration).abs();
        if (durationDiff.compareTo(durationTolerance) > 0) {
            return Optional.of(
                    "duration differs by "
                            + durationDiff.toMinutes()
                            + " min (existing "
                            + existingDuration.toMinutes()
                            + " min, uploaded "
                            + newDuration.toMinutes()
                            + " min)");
        }

        final var existingMaxDepth = maxDepth(existingMeasurements);
        final var newMaxDepth = maxDepth(newMeasurements);
        if (existingMaxDepth != null
                && newMaxDepth != null
                && Math.abs(existingMaxDepth - newMaxDepth) > MAX_DEPTH_TOLERANCE_METERS) {
            return Optional.of(
                    "max depth differs by "
                            + String.format("%.1f", Math.abs(existingMaxDepth - newMaxDepth))
                            + "m (existing "
                            + existingMaxDepth
                            + "m, uploaded "
                            + newMaxDepth
                            + "m)");
        }

        for (final var fraction : CURVE_SAMPLE_FRACTIONS) {
            final var existingDepth =
                    depthAtFraction(existingMeasurements, existingStart, existingEnd, fraction);
            final var newDepth = depthAtFraction(newMeasurements, newStart, newEnd, fraction);
            if (existingDepth != null
                    && newDepth != null
                    && Math.abs(existingDepth - newDepth) > CURVE_TOLERANCE_METERS) {
                return Optional.of(
                        "depth profile shape doesn't match at "
                                + Math.round(fraction * 100)
                                + "% of the dive (existing "
                                + String.format("%.1f", existingDepth)
                                + "m, uploaded "
                                + String.format("%.1f", newDepth)
                                + "m)");
            }
        }

        return Optional.empty();
    }

    private static Duration maxDuration(final Duration a, final Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static @Nullable Double maxDepth(final List<DiveMeasurement> measurements) {
        return measurements.stream().mapToDouble(DiveMeasurement::depth).max().stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    /**
     * Depth of whichever measurement is closest in time to {@code start + fraction*(end-start)}.
     */
    private static @Nullable Double depthAtFraction(
            final List<DiveMeasurement> measurements,
            final Instant start,
            final Instant end,
            final double fraction) {
        if (measurements.isEmpty()) {
            return null;
        }
        final var target =
                start.plusMillis(Math.round(Duration.between(start, end).toMillis() * fraction));
        return measurements.stream()
                .min(
                        Comparator.comparingLong(
                                m -> Math.abs(Duration.between(target, m.time()).toMillis())))
                .map(DiveMeasurement::depth)
                .orElse(null);
    }
}
