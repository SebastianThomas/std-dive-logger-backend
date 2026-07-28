package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmenter;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class AnalyticsSegmentService {

    public Stream<DiveProfileSegment> createSegmentForProfile(final DiveProfile profile) {
        return Stream.of(
                new DiveProfileSegment(
                        profile, 0, DiveProfileSegmentType.UNKNOWN, measurementsOf(profile)));
    }

    public Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        final var measurements = profile.measurements();
        if (measurements == null || measurements.isEmpty()) {
            return createSegmentForProfile(profile);
        }

        return DiveProfileSegmenter.segment(measurements).stream().map(d -> d.toSegment(profile));
    }

    // Analytics processing always operates on profiles fetched with measurements included.
    private static List<DiveMeasurementWithId> measurementsOf(final DiveProfile profile) {
        return Objects.requireNonNull(
                profile.measurements(), "Analytics requires a profile fetched with measurements");
    }
}
