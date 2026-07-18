package ch.sthomas.stddivelogger.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class DiveProfileEntityTest {

    private static DiveMeasurementEntity measurementAt(final Instant time, final double depth) {
        final var measurement =
                new DiveMeasurement(
                        time,
                        new Temperature(20, Temperature.TemperatureUnit.CELSIUS),
                        depth,
                        null,
                        List.of(new DecoStop("mandatory", 6, 60)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        return new DiveMeasurementEntity(measurement, null);
    }

    @Test
    void replaceMeasurementsUpdatesStartEndAndProfileBackref() {
        final var profile = new DiveProfileEntity(null, Instant.EPOCH, Instant.EPOCH, List.of());

        final var newStart = Instant.parse("2026-01-01T10:00:00Z");
        final var newEnd = Instant.parse("2026-01-01T11:00:00Z");
        final var newMeasurements =
                List.of(measurementAt(newStart, 10), measurementAt(newEnd, 5));

        profile.replaceMeasurements(newMeasurements, newStart, newEnd);

        assertEquals(newStart, profile.getStart());
        assertEquals(newEnd, profile.getEnd());
        assertEquals(newMeasurements.size(), profile.getMeasurements().size());
        assertEquals(
                List.of(newStart, newEnd),
                profile.getMeasurements().stream().map(DiveMeasurementEntity::toRecord)
                        .map(DiveMeasurement::time)
                        .toList());
    }
}
