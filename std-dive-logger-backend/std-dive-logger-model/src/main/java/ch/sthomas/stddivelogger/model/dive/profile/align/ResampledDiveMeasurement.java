package ch.sthomas.stddivelogger.model.dive.profile.align;

import java.time.Instant;

public record ResampledDiveMeasurement(Instant time, double depth) {}
