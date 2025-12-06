package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentInfo;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;

import com.google.common.math.Stats;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

@Service
public class AnalyticsSegmentService {

    private static final int WINDOW_SIZE = 5;

    public Stream<DiveProfileSegment> createSegmentForProfile(final DiveProfile profile) {
        return Stream.of(new DiveProfileSegment(profile, 0, profile.measurements()));
    }

    public Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        final var len = profile.measurements().size();
        if (len <= WINDOW_SIZE) {
            return createSegmentForProfile(profile);
        }

        record MeasurementsWithType(
                int startIdx,
                ArrayList<DiveMeasurementWithId> measurements,
                DiveProfileSegmentType type) {
            DiveProfileSegment toSegment(final DiveProfile profile) {
                return new DiveProfileSegment(profile, startIdx, measurements());
            }
        }

        class AnalyticsSegmentGathererState {
            private final AtomicInteger currentIdx;
            private final ArrayList<MeasurementsWithType> measurements;
            private List<DiveMeasurementWithId> lastProcessedWindow;

            public AnalyticsSegmentGathererState() {
                this.currentIdx = new AtomicInteger(0);
                this.measurements = new ArrayList<>();
            }

            public ArrayList<MeasurementsWithType> measurements() {
                return measurements;
            }

            public void update(final List<DiveMeasurementWithId> lastProcessedWindow) {
                this.lastProcessedWindow = lastProcessedWindow;
                currentIdx.incrementAndGet();
            }

            public List<DiveMeasurementWithId> lastProcessedWindow() {
                return lastProcessedWindow;
            }
        }

        return profile.measurements().stream()
                .gather(Gatherers.windowSliding(WINDOW_SIZE))
                .gather(
                        Gatherer
                                .<List<DiveMeasurementWithId>, AnalyticsSegmentGathererState,
                                        MeasurementsWithType>
                                        ofSequential(
                                                AnalyticsSegmentGathererState::new,
                                                (state, next, downstream) -> {
                                                    final var info = infoForMeasurements(next);
                                                    final var acc = state.measurements();
                                                    if (acc.isEmpty()
                                                            || info.type()
                                                                    != acc.getLast().type()) {
                                                        acc.add(
                                                                new MeasurementsWithType(
                                                                        state.currentIdx.get(),
                                                                        new ArrayList<>(),
                                                                        info.type()));
                                                    }
                                                    acc.getLast()
                                                            .measurements()
                                                            .add(next.getFirst());
                                                    state.update(next);
                                                    return true;
                                                },
                                                (acc, downstream) -> {
                                                    final var lastWindowSize =
                                                            acc.lastProcessedWindow().size();
                                                    acc.measurements()
                                                            .getLast()
                                                            .measurements()
                                                            .addAll(
                                                                    acc.lastProcessedWindow.subList(
                                                                            1, lastWindowSize));
                                                    acc.measurements().forEach(downstream::push);
                                                }))
                .map(d -> d.toSegment(profile));
    }

    public DiveProfileSegmentInfo infoForMeasurements(
            final List<DiveMeasurementWithId> measurements) {
        final var trivial =
                toSegmentInfo(
                                measurements,
                                (a, b) -> a <= 1 && b <= 1,
                                DiveProfileSegmentType.SURFACE)
                        .or(
                                () ->
                                        toSegmentInfo(
                                                measurements,
                                                (a, b) -> a < b,
                                                DiveProfileSegmentType.DESCENT))
                        .or(
                                () ->
                                        toSegmentInfo(
                                                measurements,
                                                (a, b) -> a > b,
                                                DiveProfileSegmentType.ASCENT));
        if (trivial.isPresent()) {
            return trivial.get();
        }
        final var summaryStats =
                measurements.stream()
                        .map(DiveMeasurementWithId::measurement)
                        .map(DiveMeasurement::depth)
                        .collect(Stats.toStats());
        if (summaryStats.populationStandardDeviation() < 2) {
            return DiveProfileSegmentInfo.ofType(DiveProfileSegmentType.HOLD_LEVEL, measurements);
        }
        return DiveProfileSegmentInfo.ofType(DiveProfileSegmentType.UNKNOWN, measurements);
    }

    private Optional<DiveProfileSegmentInfo> toSegmentInfo(
            final List<DiveMeasurementWithId> measurements,
            final BiPredicate<Double, Double> comparator,
            final DiveProfileSegmentType type) {
        if (isMonotone(measurements, comparator)) {
            return Optional.of(DiveProfileSegmentInfo.ofType(type, measurements));
        }
        return Optional.empty();
    }

    private boolean isMonotone(
            final List<DiveMeasurementWithId> measurements,
            final BiPredicate<Double, Double> comparator) {
        return measurements.stream()
                .gather(Gatherers.windowSliding(2))
                .allMatch(
                        pair ->
                                comparator.test(
                                        pair.getFirst().measurement().depth(),
                                        pair.getLast().measurement().depth()));
    }
}
