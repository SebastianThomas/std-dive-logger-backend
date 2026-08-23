package ch.sthomas.stddivelogger.model.controller.dive.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.junit.jupiter.api.Test;

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
}
