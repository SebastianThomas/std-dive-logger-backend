package ch.sthomas.stddivelogger.model.controller.dive.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.sthomas.stddivelogger.model.dive.profile.TimeRange;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class DiveProfileUploadTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static DiveMeasurement measurement(final int offsetSeconds, final double depth) {
        return new DiveMeasurement(
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
                null);
    }

    private static DiveProfileUpload steadyDescentUpload() {
        final var measurements = new ArrayList<DiveMeasurement>();
        for (var t = 0; t <= 300; t += 10) {
            measurements.add(measurement(t, Math.min(20.0, t / 10.0)));
        }
        return new DiveProfileUpload(
                1L, measurements.getFirst().time(), measurements.getLast().time(), measurements);
    }

    @Test
    void returnsTheSameInstanceWhenNeitherBoundIsGiven() {
        final var upload = steadyDescentUpload();
        assertSame(upload, upload.trimmed(null, null));
    }

    @Test
    void trimsOnlyTheEndWhenOnlyTrimEndIsGiven() {
        final var upload = steadyDescentUpload();
        final var trimEnd = START.plusSeconds(200);

        final var trimmed = upload.trimmed(null, trimEnd);

        assertEquals(upload.measurements().getFirst().time(), trimmed.start());
        assertEquals(trimEnd, trimmed.end());
        assertEquals(21, trimmed.measurements().size()); // 0,10,...,200 inclusive
    }

    @Test
    void trimsOnlyTheStartWhenOnlyTrimStartIsGiven() {
        final var upload = steadyDescentUpload();
        final var trimStart = START.plusSeconds(100);

        final var trimmed = upload.trimmed(trimStart, null);

        assertEquals(trimStart, trimmed.start());
        assertEquals(upload.measurements().getLast().time(), trimmed.end());
        assertEquals(21, trimmed.measurements().size()); // 100,110,...,300 inclusive
    }

    @Test
    void trimsBothEndsWhenBothAreGiven() {
        final var upload = steadyDescentUpload();

        final var trimmed = upload.trimmed(START.plusSeconds(100), START.plusSeconds(200));

        assertEquals(11, trimmed.measurements().size()); // 100,110,...,200 inclusive
    }

    @Test
    void refusesARangeThatWouldLeaveFewerThanTwoMeasurements() {
        final var upload = steadyDescentUpload();
        assertThrows(
                IllegalArgumentException.class,
                () -> upload.trimmed(START.plusSeconds(295), START.plusSeconds(296)));
    }

    @Test
    void refusesATrimEndAtOrBeforeTheEffectiveStart() {
        final var upload = steadyDescentUpload();
        assertThrows(
                IllegalArgumentException.class,
                () -> upload.trimmed(START.plusSeconds(200), START.plusSeconds(100)));
    }

    @Test
    void returnsTheSameInstanceWhenTheOffsetIsZero() {
        final var upload = steadyDescentUpload();
        assertSame(upload, upload.shifted(Duration.ZERO));
    }

    @Test
    void shiftsStartEndAndEveryMeasurementByTheSameOffset() {
        final var upload = steadyDescentUpload();
        final var offset = Duration.ofHours(8);

        final var shifted = upload.shifted(offset);

        assertEquals(upload.start().plus(offset), shifted.start());
        assertEquals(upload.end().plus(offset), shifted.end());
        assertEquals(upload.measurements().size(), shifted.measurements().size());
        for (var i = 0; i < upload.measurements().size(); i++) {
            assertEquals(
                    upload.measurements().get(i).time().plus(offset),
                    shifted.measurements().get(i).time());
            assertEquals(
                    upload.measurements().get(i).depth(), shifted.measurements().get(i).depth());
        }
    }

    @Test
    void shiftsBackwardsForANegativeOffset() {
        final var upload = steadyDescentUpload();
        final var offset = Duration.ofHours(-2);

        final var shifted = upload.shifted(offset);

        assertEquals(upload.start().plus(offset), shifted.start());
        assertEquals(upload.end().plus(offset), shifted.end());
    }

    @Test
    void aCutKeepsTheClockAndLeavesAGapBetweenTheSamplesAroundIt() {
        final var upload = steadyDescentUpload();

        final var cut =
                upload.trimmed(
                        null,
                        null,
                        List.of(new TimeRange(START.plusSeconds(95), START.plusSeconds(205))));

        // 100..200 gone; 90 and 210 stay where they were.
        assertEquals(31 - 11, cut.measurements().size());
        assertEquals(upload.start(), cut.start());
        assertEquals(upload.end(), cut.end());
        assertEquals(
                List.of(new TimeRange(START.plusSeconds(90), START.plusSeconds(210))), cut.gaps());
    }

    @Test
    void aCutBetweenTwoSamplesRemovesNothingAndLeavesNoGap() {
        final var upload = steadyDescentUpload();

        final var cut =
                upload.trimmed(
                        null,
                        null,
                        List.of(new TimeRange(START.plusSeconds(101), START.plusSeconds(109))));

        assertEquals(upload.measurements(), cut.measurements());
        assertEquals(List.of(), cut.gaps());
    }

    @Test
    void aLaterTrimClipsAnEarlierGapAndAShiftMovesIt() {
        final var withGap =
                steadyDescentUpload()
                        .trimmed(
                                null,
                                null,
                                List.of(
                                        new TimeRange(
                                                START.plusSeconds(95), START.plusSeconds(205))));

        final var trimmed =
                withGap.trimmed(START.plusSeconds(50), null).shifted(Duration.ofHours(1));

        assertEquals(
                List.of(
                        new TimeRange(
                                START.plusSeconds(90).plus(Duration.ofHours(1)),
                                START.plusSeconds(210).plus(Duration.ofHours(1)))),
                trimmed.gaps());
        // A trim past the gap drops it.
        assertEquals(List.of(), withGap.trimmed(START.plusSeconds(220), null).gaps());
    }

    @Test
    void cuttingAwayAlmostEverythingIsRefused() {
        final var upload = steadyDescentUpload();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        upload.trimmed(
                                null, null, List.of(new TimeRange(START, START.plusSeconds(295)))));
    }
}
