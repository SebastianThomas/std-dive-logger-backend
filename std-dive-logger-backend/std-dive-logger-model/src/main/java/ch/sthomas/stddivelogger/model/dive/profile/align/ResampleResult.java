package ch.sthomas.stddivelogger.model.dive.profile.align;

import java.util.List;

public record ResampleResult(List<ResampledDiveMeasurement> resampled, ResamplingInfo info) {}
