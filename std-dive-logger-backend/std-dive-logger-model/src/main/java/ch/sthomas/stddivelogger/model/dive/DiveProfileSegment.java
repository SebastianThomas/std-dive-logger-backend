package ch.sthomas.stddivelogger.model.dive;

import java.util.List;

public record DiveProfileSegment(
        DiveProfile profile, int firstMeasurementIdx, List<DiveMeasurementWithId> measurements) {}
