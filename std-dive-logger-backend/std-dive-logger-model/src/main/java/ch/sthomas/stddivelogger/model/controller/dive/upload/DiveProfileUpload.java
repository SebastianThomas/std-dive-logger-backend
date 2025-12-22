package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import java.time.Instant;
import java.util.List;

public record DiveProfileUpload(
        long diveComputerId, Instant start, Instant end, List<DiveMeasurement> measurements) {}
