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
import java.util.Random;
import java.util.stream.Collectors;

class DiveProfileSegmenterTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static DiveMeasurementWithId sample(final int id, final int offsetSeconds, final double depth) {
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
                        null),
                id);
    }

    @Test
    void steadyDescentIsOneDescentSegment() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        for (var t = 0; t <= 60; t++) {
            measurements.add(sample(id++, t, Math.min(20.0, t * 1.0)));
        }

        final var segments = DiveProfileSegmenter.segment(measurements);
        final var descentSegments =
                segments.stream().filter(s -> s.type() == DiveProfileSegmentType.DESCENT).toList();

        assertEquals(1, descentSegments.size(), "descent should not be broken into multiple segments");
        assertTrue(descentSegments.getFirst().measurements().size() >= 15);
    }

    @Test
    void smallBuoyancyNoiseDuringHoldDoesNotProduceAscentDescentChatter() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        final var random = new Random(42);
        var id = 0;
        // descend to 20m first
        for (var t = 0; t <= 20; t++) {
            measurements.add(sample(id++, t, Math.min(20.0, t * 1.0)));
        }
        // hold at 20m for 5 minutes with +/-0.3m noise, oscillating every couple of seconds
        for (var t = 21; t <= 320; t++) {
            final var noise = (random.nextDouble() - 0.5) * 0.6;
            measurements.add(sample(id++, t, 20.0 + noise));
        }

        final var segments = DiveProfileSegmenter.segment(measurements);
        final var chatterSegments =
                segments.stream()
                        .filter(
                                s ->
                                        s.type() == DiveProfileSegmentType.ASCENT
                                                || s.type() == DiveProfileSegmentType.DESCENT)
                        .filter(s -> s.startIdx() > 20)
                        .toList();

        assertTrue(
                chatterSegments.isEmpty(),
                "buoyancy noise during a hold should stay HOLD_LEVEL, got: "
                        + chatterSegments.stream().map(MeasurementsWithType::type).toList());
    }

    @Test
    void shortStrongAscentDuringHoldIsStillDetected() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        // hold at 20m for a while
        for (var t = 0; t <= 60; t++) {
            measurements.add(sample(id++, t, 20.0));
        }
        // quick ascent to 15m over 15 seconds (20 m/min, well above threshold)
        for (var t = 61; t <= 75; t++) {
            final var depth = 20.0 - (t - 60) * (5.0 / 15.0);
            measurements.add(sample(id++, t, depth));
        }
        // hold at 15m again
        for (var t = 76; t <= 150; t++) {
            measurements.add(sample(id++, t, 15.0));
        }

        final var segments = DiveProfileSegmenter.segment(measurements);
        final var ascentSegments =
                segments.stream().filter(s -> s.type() == DiveProfileSegmentType.ASCENT).toList();

        assertEquals(1, ascentSegments.size(), "the short quick ascent should not be smoothed away");
        final var ascent = ascentSegments.getFirst();
        final var duration =
                Duration.between(
                        ascent.measurements().getFirst().measurement().time(),
                        ascent.measurements().getLast().measurement().time());
        assertTrue(
                duration.getSeconds() >= 8,
                "detected ascent segment should roughly span the real event, got " + duration);
    }

    @Test
    void segmentsAreContiguousAndCoverAllMeasurements() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        for (var t = 0; t <= 40; t++) {
            measurements.add(sample(id++, t, Math.min(15.0, t * 1.0)));
        }
        for (var t = 41; t <= 90; t++) {
            measurements.add(sample(id++, t, 15.0));
        }
        for (var t = 91; t <= 106; t++) {
            measurements.add(sample(id++, t, 15.0 - (t - 90) * 1.0));
        }

        final var segments = DiveProfileSegmenter.segment(measurements);
        var expectedNextStart = 0;
        var totalMeasurements = 0;
        for (final var segment : segments) {
            assertEquals(expectedNextStart, segment.startIdx());
            expectedNextStart += segment.measurements().size();
            totalMeasurements += segment.measurements().size();
        }
        assertEquals(measurements.size(), totalMeasurements);
        assertEquals(
                measurements.stream().map(DiveMeasurementWithId::id).collect(Collectors.toList()),
                segments.stream()
                        .flatMap(s -> s.measurements().stream())
                        .map(DiveMeasurementWithId::id)
                        .collect(Collectors.toList()));
    }

    @Test
    void smoothedRatesMatchSegmentClassificationSignConvention() {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        var id = 0;
        // steady 10 m/min descent from 0 to 20m
        for (var t = 0; t <= 120; t++) {
            measurements.add(sample(id++, t, Math.min(20.0, t * (10.0 / 60.0))));
        }
        // steady 10 m/min ascent back to the surface
        for (var t = 121; t <= 241; t++) {
            measurements.add(sample(id++, t, Math.max(0.0, 20.0 - (t - 120) * (10.0 / 60.0))));
        }

        final var rates = DiveProfileSegmenter.smoothedRates(measurements);
        assertEquals(measurements.size(), rates.length);

        final var midDescent = rates[60];
        final var midAscent = rates[180];
        assertTrue(midDescent > 8 && midDescent < 12, "expected ~10 m/min descent, got " + midDescent);
        assertTrue(midAscent < -8 && midAscent > -12, "expected ~-10 m/min ascent, got " + midAscent);
    }
}
