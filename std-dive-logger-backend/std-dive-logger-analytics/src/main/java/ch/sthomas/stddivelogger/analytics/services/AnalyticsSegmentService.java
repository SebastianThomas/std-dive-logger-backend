package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsSegmentGathererState;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

@Service
public class AnalyticsSegmentService {

    private static final int WINDOW_SIZE = 10;

    public Stream<DiveProfileSegment> createSegmentForProfile(final DiveProfile profile) {
        return Stream.of(
                new DiveProfileSegment(
                        profile, 0, DiveProfileSegmentType.UNKNOWN, measurementsOf(profile)));
    }

    public Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        final var measurements = measurementsOf(profile);
        if (measurements.size() <= WINDOW_SIZE) {
            return createSegmentForProfile(profile);
        }

        return measurements.stream()
                .gather(Gatherers.windowSliding(WINDOW_SIZE))
                .gather(AnalyticsSegmentGathererState.gatherer())
                .map(d -> d.toSegment(profile));
    }

    // Analytics processing always operates on profiles fetched with measurements included.
    private static List<DiveMeasurementWithId> measurementsOf(final DiveProfile profile) {
        return Objects.requireNonNull(
                profile.measurements(), "Analytics requires a profile fetched with measurements");
    }
}
