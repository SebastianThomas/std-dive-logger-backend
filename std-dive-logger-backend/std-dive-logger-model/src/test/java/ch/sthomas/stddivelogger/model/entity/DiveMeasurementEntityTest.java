package ch.sthomas.stddivelogger.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class DiveMeasurementEntityTest {

    @Test
    void decoStopsSurviveConstructionAndRoundTrip() {
        final var deco = List.of(new DecoStop("mandatory", 6, 60));
        final var measurement =
                new DiveMeasurement(
                        Instant.now(),
                        new Temperature(20, Temperature.TemperatureUnit.CELSIUS),
                        25,
                        null,
                        deco,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        final var entity = new DiveMeasurementEntity(measurement, null);

        assertEquals(deco, entity.getDecoStops());
        assertEquals(deco, entity.toRecord().deco());
    }
}
