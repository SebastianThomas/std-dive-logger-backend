package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record DiveMeasurement(
        Instant time,
        Temperature temperature,
        double depth,
        Duration ndl,
        List<DecoStop> deco,
        @Nullable Gas gas) {}
