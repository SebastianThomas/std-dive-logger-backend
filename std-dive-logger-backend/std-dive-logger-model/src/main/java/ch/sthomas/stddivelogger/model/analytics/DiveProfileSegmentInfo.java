package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import java.util.List;

public record DiveProfileSegmentInfo(
        DiveProfileSegmentType type, double startDepth, double endDepth) {
    public static DiveProfileSegmentInfo ofType(
            final DiveProfileSegmentType type, final List<DiveMeasurementWithId> measurements) {
        return new DiveProfileSegmentInfo(
                type,
                measurements.getFirst().measurement().depth(),
                measurements.getLast().measurement().depth());
    }
}
