package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a dive profile into contiguous SURFACE / DESCENT / HOLD_LEVEL / ASCENT segments.
 *
 * <p>Each measurement is first classified from its {@link DiveProfileRateCalculator smoothed rate}.
 * A raw, per-sample classification still flickers on brief noise (e.g. a diver briefly drifting up
 * half a meter while holding a stop), so a type change is only committed once it has persisted for
 * at least {@link #MIN_SEGMENT_DURATION}. Once committed, the whole span back to where the change
 * actually started is relabelled, so segment boundaries land on the real transition rather than
 * lagging by a debounce delay. Genuine short ascents/descents (as brief as ~10-20s) comfortably
 * clear that duration and are kept; sub-second-to-few-second wobble does not.
 */
public final class DiveProfileSegmenter {

    private static final Duration RATE_WINDOW_HALF = Duration.ofSeconds(3);
    private static final Duration MIN_SEGMENT_DURATION = Duration.ofSeconds(6);
    private static final double RATE_THRESHOLD_METERS_PER_MINUTE = 3.0;
    private static final double SURFACE_DEPTH_METERS = 1.0;

    private DiveProfileSegmenter() {}

    public static List<MeasurementsWithType> segment(
            final List<DiveMeasurementWithId> measurements) {
        if (measurements.isEmpty()) {
            return List.of();
        }
        final var types = classifyWithDebounce(measurements);
        return groupByType(measurements, types);
    }

    /**
     * The same smoothed rate (m/min, positive while descending) that segment classification itself
     * is based on, exposed so consumers needing the continuous signal (e.g. an ascent/ descent rate
     * graph) can use exactly the numbers segmentation used rather than recomputing their own from
     * scratch.
     */
    public static double[] smoothedRates(final List<DiveMeasurementWithId> measurements) {
        return DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                measurements, RATE_WINDOW_HALF);
    }

    private static DiveProfileSegmentType[] classifyWithDebounce(
            final List<DiveMeasurementWithId> measurements) {
        final var rates =
                DiveProfileRateCalculator.smoothedRatesInMetersPerMinute(
                        measurements, RATE_WINDOW_HALF);
        final var n = measurements.size();
        final var types = new DiveProfileSegmentType[n];

        DiveProfileSegmentType committedType = null;
        DiveProfileSegmentType pendingType = null;
        var pendingStart = -1;

        for (var i = 0; i < n; i++) {
            final var observed = classify(measurements.get(i).measurement(), rates[i]);
            if (committedType == null) {
                committedType = observed;
            }
            if (observed == committedType) {
                pendingType = null;
                pendingStart = -1;
                types[i] = committedType;
                continue;
            }
            if (observed != pendingType) {
                pendingType = observed;
                pendingStart = i;
            }
            final var pendingDuration =
                    Duration.between(
                            measurements.get(pendingStart).measurement().time(),
                            measurements.get(i).measurement().time());
            if (pendingDuration.compareTo(MIN_SEGMENT_DURATION) >= 0) {
                for (var j = pendingStart; j <= i; j++) {
                    types[j] = pendingType;
                }
                committedType = pendingType;
                pendingType = null;
                pendingStart = -1;
            } else {
                types[i] = committedType;
            }
        }
        return types;
    }

    private static DiveProfileSegmentType classify(
            final DiveMeasurement measurement, final double rateMetersPerMinute) {
        if (measurement.depth() <= SURFACE_DEPTH_METERS) {
            return DiveProfileSegmentType.SURFACE;
        }
        if (Math.abs(rateMetersPerMinute) < RATE_THRESHOLD_METERS_PER_MINUTE) {
            return DiveProfileSegmentType.HOLD_LEVEL;
        }
        return rateMetersPerMinute > 0
                ? DiveProfileSegmentType.DESCENT
                : DiveProfileSegmentType.ASCENT;
    }

    private static List<MeasurementsWithType> groupByType(
            final List<DiveMeasurementWithId> measurements, final DiveProfileSegmentType[] types) {
        final var result = new ArrayList<MeasurementsWithType>();
        var currentMeasurements = new ArrayList<DiveMeasurementWithId>();
        var currentType = types[0];
        var currentStart = 0;
        for (var i = 0; i < measurements.size(); i++) {
            if (types[i] != currentType) {
                result.add(
                        new MeasurementsWithType(currentStart, currentMeasurements, currentType));
                currentMeasurements = new ArrayList<>();
                currentType = types[i];
                currentStart = i;
            }
            currentMeasurements.add(measurements.get(i));
        }
        result.add(new MeasurementsWithType(currentStart, currentMeasurements, currentType));
        return result;
    }
}
