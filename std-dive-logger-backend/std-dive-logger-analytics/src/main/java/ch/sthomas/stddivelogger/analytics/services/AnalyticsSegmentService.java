package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsSegmentGathererState;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;

import org.springframework.stereotype.Service;

import java.util.stream.Gatherers;
import java.util.stream.Stream;

@Service
public class AnalyticsSegmentService {

    private static final int WINDOW_SIZE = 10;

    public Stream<DiveProfileSegment> createSegmentForProfile(final DiveProfile profile) {
        return Stream.of(new DiveProfileSegment(profile, 0, profile.measurements()));
    }

    public Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        final var len = profile.measurements().size();
        if (len <= WINDOW_SIZE) {
            return createSegmentForProfile(profile);
        }

        return profile.measurements().stream()
                .gather(Gatherers.windowSliding(WINDOW_SIZE))
                .gather(AnalyticsSegmentGathererState.gatherer())
                .map(d -> d.toSegment(profile));
    }
}
