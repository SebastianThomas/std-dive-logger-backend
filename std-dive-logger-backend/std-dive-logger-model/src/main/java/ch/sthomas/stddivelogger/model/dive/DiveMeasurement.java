package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public record DiveMeasurement(
        Instant time, Temperature temperature, double depth, Duration ndl, @Nullable Gas gas) {}
