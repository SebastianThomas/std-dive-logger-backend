package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import java.time.Duration;
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
        final var start = measurements.getFirst().measurement().time();
        for (var i = 0; i < n; i++) {
            final var measurement = measurements.get(i).measurement();
            seconds[i] = Duration.between(start, measurement.time()).toMillis() / 1000.0;
            depths[i] = measurement.depth();
        }

        final var halfWindowSeconds = halfWindow.toMillis() / 1000.0;
        final var rates = new double[n];

        var lo = 0;
        var hi = 0;
        var sumX = 0.0;
        var sumY = 0.0;
        var sumXY = 0.0;
        var sumXX = 0.0;
        for (var i = 0; i < n; i++) {
            final var lowerBound = seconds[i] - halfWindowSeconds;
            final var upperBound = seconds[i] + halfWindowSeconds;
            while (hi < n && seconds[hi] <= upperBound) {
                sumX += seconds[hi];
                sumY += depths[hi];
                sumXY += seconds[hi] * depths[hi];
                sumXX += seconds[hi] * seconds[hi];
                hi++;
            }
            while (lo < hi && seconds[lo] < lowerBound) {
                sumX -= seconds[lo];
                sumY -= depths[lo];
                sumXY -= seconds[lo] * depths[lo];
                sumXX -= seconds[lo] * seconds[lo];
                lo++;
            }
            final var count = hi - lo;
            if (count < 2) {
                rates[i] = 0.0;
                continue;
            }
            final var meanX = sumX / count;
            final var meanY = sumY / count;
            final var covarianceXY = sumXY / count - meanX * meanY;
            final var varianceX = sumXX / count - meanX * meanX;
            final var slopePerSecond = varianceX > 1e-9 ? covarianceXY / varianceX : 0.0;
            rates[i] = slopePerSecond * 60.0;
        }
        return rates;
    }
}
