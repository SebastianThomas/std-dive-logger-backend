package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Computes a smoothed depth-change rate (in meters/minute, positive while descending) for every
 * measurement of a dive profile.
 *
 * <p>Rather than differencing two adjacent, possibly jittery samples, the rate at a given
 * measurement is the slope of a least-squares regression line fitted through all measurements
 * within a small time window centered on it. This averages out sensor noise and normal buoyancy
 * wobble without depending on a fixed sample count, so it behaves the same regardless of how
 * densely a dive computer sampled.
 */
public final class DiveProfileRateCalculator {

    private DiveProfileRateCalculator() {}

    public static double[] smoothedRatesInMetersPerMinute(
            final List<DiveMeasurementWithId> measurements, final Duration halfWindow) {
        final var n = measurements.size();
        final var seconds = new double[n];
        final var depths = new double[n];
        // A single corrupted sample (e.g. a NaN depth from a bad import) must not poison every
        // rate from that point onward: the sliding window below accumulates running sums
        // incrementally rather than recomputing them from scratch each step, so one NaN added in
        // stays NaN forever after (NaN - x is still NaN, so even sliding back out of the window
        // never recovers). Such a sample is instead excluded from the regression entirely, as if
        // it were never sampled - its own rate falls out at 0.0 via the `count < 2` guard below
        // rather than the regression it doesn't have a valid input for.
        final var valid = new boolean[n];
        final var start = measurements.getFirst().measurement().time();
        for (var i = 0; i < n; i++) {
            final var measurement = measurements.get(i).measurement();
            seconds[i] = Duration.between(start, measurement.time()).toMillis() / 1000.0;
            depths[i] = measurement.depth();
            valid[i] = Double.isFinite(seconds[i]) && Double.isFinite(depths[i]);
        }

        // A fixed window only captures enough points to regress against when the profile is
        // itself sampled at least that densely. Many real dive computers sample every 10-30s+ (to
        // save memory on long/deco dives), so a fixed few-second window would contain nothing but
        // the point itself - silently forcing rate=0.0 via the `count < 2` guard below for every
        // single measurement, regardless of how fast the diver was actually moving (and, since
        // segmentation classifies near-zero rates as HOLD_LEVEL, misreading the entire dive as
        // never ascending or descending). Widen the window to comfortably straddle a few of this
        // profile's own actual samples, never *narrower* than the caller's requested window.
        final var halfWindowSeconds =
                Math.max(halfWindow.toMillis() / 1000.0, minHalfWindowSecondsFor(seconds));
        final var rates = new double[n];

        var lo = 0;
        var hi = 0;
        var validCount = 0;
        var sumX = 0.0;
        var sumY = 0.0;
        var sumXY = 0.0;
        var sumXX = 0.0;
        for (var i = 0; i < n; i++) {
            final var lowerBound = seconds[i] - halfWindowSeconds;
            final var upperBound = seconds[i] + halfWindowSeconds;
            while (hi < n && seconds[hi] <= upperBound) {
                if (valid[hi]) {
                    sumX += seconds[hi];
                    sumY += depths[hi];
                    sumXY += seconds[hi] * depths[hi];
                    sumXX += seconds[hi] * seconds[hi];
                    validCount++;
                }
                hi++;
            }
            while (lo < hi && seconds[lo] < lowerBound) {
                if (valid[lo]) {
                    sumX -= seconds[lo];
                    sumY -= depths[lo];
                    sumXY -= seconds[lo] * depths[lo];
                    sumXX -= seconds[lo] * seconds[lo];
                    validCount--;
                }
                lo++;
            }
            if (!valid[i] || validCount < 2) {
                rates[i] = 0.0;
                continue;
            }
            final var meanX = sumX / validCount;
            final var meanY = sumY / validCount;
            final var covarianceXY = sumXY / validCount - meanX * meanY;
            final var varianceX = sumXX / validCount - meanX * meanX;
            final var slopePerSecond = varianceX > 1e-9 ? covarianceXY / varianceX : 0.0;
            // Defensive floor: the arithmetic above is well-defined for any finite, valid inputs,
            // so this should be unreachable - but "should be unreachable" is exactly the kind of
            // guarantee that isn't worth trusting blindly for a value that flows straight into an
            // API response and a chart's SVG path data.
            rates[i] = Double.isFinite(slopePerSecond) ? slopePerSecond * 60.0 : 0.0;
        }
        return rates;
    }

    /**
     * At least two samples on each side of a window's center (so a regression can be fit through a
     * handful of real points, not just clear the bare {@code count >= 2} minimum) - derived from
     * the profile's own median inter-sample gap, robust to a single dropped/duplicate sample
     * skewing a mean. Zero for a profile too short to have a gap at all.
     */
    private static double minHalfWindowSecondsFor(final double[] seconds) {
        final var n = seconds.length;
        if (n < 2) {
            return 0.0;
        }
        final var gaps = new double[n - 1];
        for (var i = 1; i < n; i++) {
            gaps[i - 1] = seconds[i] - seconds[i - 1];
        }
        Arrays.sort(gaps);
        final var medianGap = gaps[gaps.length / 2];
        return medianGap * 2;
    }
}
