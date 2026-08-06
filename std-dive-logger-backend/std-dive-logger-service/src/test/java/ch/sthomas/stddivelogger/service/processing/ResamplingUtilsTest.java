package ch.sthomas.stddivelogger.service.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.dive.profile.align.ResamplingInfo;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class ResamplingUtilsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static DiveMeasurementWithId sample(final int offsetSeconds, final double depth) {
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
                        null),
                offsetSeconds + 1L);
    }

    @Test
    void skipsANonFiniteDepthMeasurementRatherThanPropagatingNan() {
        final var measurements =
                List.of(sample(0, 0), sample(1, Double.NaN), sample(2, 10), sample(3, 20));
        final var info = ResamplingUtils.getResamplingInfo(measurements);

        final var resampled = ResamplingUtils.resampleMeasurements(measurements, info);

        assertFalse(resampled.isEmpty());
        assertTrue(resampled.stream().allMatch(m -> Double.isFinite(m.depth())));
    }

    @Test
    void returnsEmptyWhenEveryMeasurementHasANonFiniteDepth() {
        final var measurements = List.of(sample(0, Double.NaN), sample(1, Double.NaN));
        final var info = new ResamplingInfo(Duration.ofSeconds(1), START);

        final var resampled = ResamplingUtils.resampleMeasurements(measurements, info);

        assertTrue(resampled.isEmpty());
    }

    @Test
    void resamplesNormallyWhenAllDepthsAreFinite() {
        final var measurements = List.of(sample(0, 0), sample(1, 10), sample(2, 20));
        final var info = ResamplingUtils.getResamplingInfo(measurements);

        final var resampled = ResamplingUtils.resampleMeasurements(measurements, info);

        assertEquals(3, resampled.size());
        assertEquals(0, resampled.get(0).depth());
        assertEquals(20, resampled.get(2).depth());
    }
}
