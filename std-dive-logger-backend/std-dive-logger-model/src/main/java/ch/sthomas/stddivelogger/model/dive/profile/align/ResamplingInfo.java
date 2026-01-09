package ch.sthomas.stddivelogger.model.dive.profile.align;

import java.time.Duration;
import java.time.Instant;

public record ResamplingInfo(Duration sampleRate, Instant baseTime) {}
