package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;

import jakarta.annotation.Nullable;

import java.util.List;

public record DiveProfileSegment(
        DiveProfile profile,
        int firstMeasurementIdx,
        DiveProfileSegmentType type,
        @Nullable List<DiveMeasurementWithId> measurements) {}
