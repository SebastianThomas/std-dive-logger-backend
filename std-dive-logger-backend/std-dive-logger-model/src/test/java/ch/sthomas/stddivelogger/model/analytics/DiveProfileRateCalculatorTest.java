package ch.sthomas.stddivelogger.model.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class DiveProfileRateCalculatorTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration DEFAULT_HALF_WINDOW = Duration.ofSeconds(3);

    private static DiveMeasurementWithId sample(
            final int id, final int offsetSeconds, final double depth) {
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        START.plusSeconds(offsetSeconds),
                        null,
                        depth,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                id);
    }

    private static List<DiveMeasurementWithId> steadyDescent(final int sampleIntervalSeconds) {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        // A real, brisk-but-normal descent: 1 m every 10s of elapsed time, sampled every
        // `sampleIntervalSeconds`, down to 30m.
        for (var t = 0; t <= 300; t += sampleIntervalSeconds) {
            measurements.add(sample(id++, t, Math.min(30.0, t / 10.0)));
        }
        return measurements;
    }

    @Test
    void densely1sSampledDescentProducesNonZeroRates() {
        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        steadyDescent(1), DEFAULT_HALF_WINDOW);
        // Mid-descent points should read close to the true 6 m/min rate (1m/10s).
        final var mid = rates.length / 2;
        assertEquals(6.0, rates[mid], 0.5);
    }

    @Test
    void sparsely10sSampledDescentIsNotFlattenedToZero() {
        // Before the fix: a fixed 3s half-window only ever contains the point itself when
        // samples are 10s apart, so every single rate was forced to exactly 0.0 by the
        // `count < 2` guard - silently reading a real descent as "holding depth".
        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        steadyDescent(10), DEFAULT_HALF_WINDOW);
        final var mid = rates.length / 2;
        assertTrue(
                Math.abs(rates[mid]) > 1.0,
                "expected a non-trivial rate mid-descent, got " + rates[mid]);
        assertEquals(6.0, rates[mid], 1.5);
    }

    @Test
    void sparsely30sSampledDescentIsNotFlattenedToZero() {
        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        steadyDescent(30), DEFAULT_HALF_WINDOW);
        final var mid = rates.length / 2;
        assertTrue(
                Math.abs(rates[mid]) > 1.0,
                "expected a non-trivial rate mid-descent, got " + rates[mid]);
    }

    @Test
    void singleCorruptedDepthDoesNotPoisonEveryLaterRate() {
        // Before the fix: the sliding window accumulates running sums incrementally rather than
        // recomputing from scratch, so a single NaN depth added to sumY/sumXY/etc. stayed NaN
        // forever after, even once that sample had long since slid back out of the window -
        // corrupting every rate for the rest of the whole profile from one bad sample.
        final var measurements = steadyDescent(1);
        final var corruptedIdx = 100;
        final var corrupted = measurements.get(corruptedIdx);
        measurements.set(
                corruptedIdx,
                new DiveMeasurementWithId(
                        new DiveMeasurement(
                                corrupted.measurement().time(),
                                null,
                                Double.NaN,
                                null,
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        corrupted.id()));

        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        measurements, DEFAULT_HALF_WINDOW);

        for (var i = 0; i < rates.length; i++) {
            assertTrue(
                    Double.isFinite(rates[i]),
                    "rate at index " + i + " was not finite: " + rates[i]);
        }
        // Far past the corrupted sample and its window, the descent rate should read normally
        // again rather than staying wrecked for the rest of the profile.
        final var lastIdx = rates.length - 1;
        assertEquals(6.0, rates[lastIdx], 0.5);
    }

    @Test
    void smoothedForDisplayReducesAnIsolatedSpikeTowardsItsNeighbors() {
        final var rates = new double[] {5.0, 5.0, 5.0, 9.0, 5.0, 5.0, 5.0};
        final var smoothed = DiveProfileRateCalculator.smoothedForDisplay(rates);

        // The spike is pulled down towards its neighbors...
        assertTrue(
                smoothed[3] < 9.0 && smoothed[3] > 5.0,
                "expected the spike to be pulled towards its neighbors, got " + smoothed[3]);
        // ...and its immediate neighbors now read a bit closer to the peak than before, so a
        // near-miss hover next to the peak sample sees a more representative value.
        assertTrue(
                smoothed[2] > 5.0 && smoothed[4] > 5.0,
                "expected the spike's neighbors to be pulled up, got "
                        + smoothed[2]
                        + " / "
                        + smoothed[4]);
        // Untouched flat regions well away from the spike stay flat.
        assertEquals(5.0, smoothed[0], 0.001);
        assertEquals(5.0, smoothed[6], 0.001);
    }

    @Test
    void smoothedForDisplayPreservesArrayLengthAndHandlesEdgesAndNonFiniteValues() {
        final var rates = new double[] {Double.NaN, 4.0, 6.0};
        final var smoothed = DiveProfileRateCalculator.smoothedForDisplay(rates);

        assertEquals(3, smoothed.length);
        for (final var v : smoothed) {
            assertTrue(Double.isFinite(v), "expected every smoothed value to be finite, got " + v);
        }
        // First index: window is [NaN, 4.0] (clamped at the left edge) - NaN is skipped, so it's
        // just the finite neighbor.
        assertEquals(4.0, smoothed[0], 0.001);
    }

    @Test
    void steadyHoldStaysNearZeroRegardlessOfSampleInterval() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        for (var t = 0; t <= 300; t += 15) {
            measurements.add(sample(id++, t, 20.0));
        }
        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        measurements, DEFAULT_HALF_WINDOW);
        final var mid = rates.length / 2;
        assertEquals(0.0, rates[mid], 0.01);
    }
}
