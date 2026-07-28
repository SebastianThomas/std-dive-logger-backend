package ch.sthomas.stddivelogger.model.dive.profile;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DiveProfileSegment(
        DiveProfile profile,
        int firstMeasurementIdx,
        DiveProfileSegmentType type,
        @Nullable List<DiveMeasurementWithId> measurements) {}
