package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;

import java.util.ArrayList;

public record MeasurementsWithType(
        int startIdx, ArrayList<DiveMeasurementWithId> measurements, DiveProfileSegmentType type) {
    public DiveProfileSegment toSegment(final DiveProfile profile) {
        return new DiveProfileSegment(profile, startIdx, type, measurements());
    }
}
