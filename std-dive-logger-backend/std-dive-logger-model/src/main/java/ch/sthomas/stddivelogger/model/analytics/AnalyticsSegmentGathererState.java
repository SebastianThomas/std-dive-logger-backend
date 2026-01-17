package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import com.google.common.math.Stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

public class AnalyticsSegmentGathererState {
    private static final Duration CUTOFF_DELAY = Duration.ofSeconds(25);
    private static final Logger logger =
            LoggerFactory.getLogger(AnalyticsSegmentGathererState.class);

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

    public static Gatherer<
                    List<DiveMeasurementWithId>,
                    AnalyticsSegmentGathererState,
                    MeasurementsWithType>
            gatherer() {
        return Gatherer
                .<List<DiveMeasurementWithId>, AnalyticsSegmentGathererState, MeasurementsWithType>
                        ofSequential(
                                AnalyticsSegmentGathererState::new,
                                AnalyticsSegmentGathererState::integrator,
                                AnalyticsSegmentGathererState::finisher);
    }

    public static boolean integrator(
            final AnalyticsSegmentGathererState state,
            final List<DiveMeasurementWithId> next,
            final Gatherer.Downstream<? super MeasurementsWithType> downstream) {
        final var info = infoForMeasurements(next);
        final var acc = state.measurements();
        if (acc.isEmpty() || info.type() != acc.getLast().type()) {
            acc.add(
                    new MeasurementsWithType(
                            state.currentIdx.get(), new ArrayList<>(), info.type()));
        }
        acc.getLast().measurements().add(next.getFirst());
        state.update(next);
        return true;
    }

    public static void finisher(
            final AnalyticsSegmentGathererState acc,
            final Gatherer.Downstream<? super MeasurementsWithType> downstream) {
        final var lastWindowSize = acc.lastProcessedWindow().size();
        acc.measurements()
                .getLast()
                .measurements()
                .addAll(acc.lastProcessedWindow.subList(1, lastWindowSize));
        acc.measurements().forEach(downstream::push);
    }

    public static DiveProfileSegmentInfo infoForMeasurements(
            final List<DiveMeasurementWithId> measurements) {
        final var result =
                toSegmentInfo(
                                measurements,
                                (a, b) -> a <= 1 && b <= 1,
                                (a, b) -> a <= 1 && b <= 1,
                                DiveProfileSegmentType.SURFACE)
                        .or(() -> toSegmentInfoHoldingLevel(measurements))
                        .or(
                                () ->
                                        toSegmentInfo(
                                                measurements,
                                                (a, b) -> a <= b,
                                                (a, b) -> a - 0.5 <= b,
                                                DiveProfileSegmentType.DESCENT))
                        .or(
                                () ->
                                        toSegmentInfo(
                                                measurements,
                                                (a, b) -> a >= b,
                                                (a, b) -> a + 0.5 >= b,
                                                DiveProfileSegmentType.ASCENT));
        if (result.isPresent()) {
            logger.trace(
                    "Found segments with info {} for measurements {}",
                    result.get(),
                    measurements.stream()
                            .map(DiveMeasurementWithId::measurement)
                            .mapToDouble(DiveMeasurement::depth)
                            .toArray());
            return result.get();
        }
        logger.info(
                "Cannot get segment type for measurements {}",
                measurements.stream()
                        .map(DiveMeasurementWithId::measurement)
                        .mapToDouble(DiveMeasurement::depth)
                        .toArray());
        return DiveProfileSegmentInfo.ofType(DiveProfileSegmentType.UNKNOWN, measurements);
    }

    private static Optional<DiveProfileSegmentInfo> toSegmentInfoHoldingLevel(
            final List<DiveMeasurementWithId> measurements) {
        final var cutoff = measurements.getFirst().measurement().time().plus(CUTOFF_DELAY);
        final var summaryStats =
                measurements.stream()
                        .map(DiveMeasurementWithId::measurement)
                        .takeWhile(m -> cutoff.isAfter(m.time()))
                        .map(DiveMeasurement::depth)
                        .collect(Stats.toStats());
        if (summaryStats.populationStandardDeviation() < 0.4
                || summaryStats.max() - 1 <= summaryStats.min()) {
            return Optional.of(
                    DiveProfileSegmentInfo.ofType(DiveProfileSegmentType.HOLD_LEVEL, measurements));
        }
        return Optional.empty();
    }

    private static Optional<DiveProfileSegmentInfo> toSegmentInfo(
            final List<DiveMeasurementWithId> measurements,
            final BiPredicate<Double, Double> comparator,
            final BiPredicate<Double, Double> globalComparator,
            final DiveProfileSegmentType type) {
        if (isMonotone(measurements, comparator, globalComparator)) {
            return Optional.of(DiveProfileSegmentInfo.ofType(type, measurements));
        }
        return Optional.empty();
    }

    private static boolean isMonotone(
            final List<DiveMeasurementWithId> measurements,
            final BiPredicate<Double, Double> comparator,
            final BiPredicate<Double, Double> globalComparator) {
        final var cutoff = measurements.getFirst().measurement().time().plus(CUTOFF_DELAY);
        final var windows =
                measurements.stream()
                        .gather(Gatherers.windowSliding(2))
                        .filter(m -> cutoff.isAfter(m.getFirst().measurement().time()))
                        .toList();
        if (!globalComparator.test(
                windows.getFirst().getFirst().measurement().depth(),
                windows.getLast().getLast().measurement().depth())) {
            return false;
        }
        final var categorized =
                windows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        pair ->
                                                comparator.test(
                                                        pair.getFirst().measurement().depth(),
                                                        pair.getLast().measurement().depth())));
        final var matching = (double) categorized.getOrDefault(true, List.of()).size();
        final var nonMatching = (double) categorized.getOrDefault(false, List.of()).size();
        return matching / (matching + nonMatching) >= 0.6;
    }
}
