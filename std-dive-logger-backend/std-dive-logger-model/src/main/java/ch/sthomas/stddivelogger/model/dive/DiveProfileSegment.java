package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;

import java.util.List;

public record DiveProfileSegment(
        DiveProfile profile,
        int firstMeasurementIdx,
        DiveProfileSegmentType type,
        List<DiveMeasurementWithId> measurements) {}
