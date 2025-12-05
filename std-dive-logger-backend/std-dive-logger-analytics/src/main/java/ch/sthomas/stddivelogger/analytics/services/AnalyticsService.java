package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsResult;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;

import com.google.common.math.Stats;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

@Service
public class AnalyticsService {
    private static final long MAX_DIVES_PER_RUN = 100;
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsDataService analyticsDataService;

    public AnalyticsService(final AnalyticsDataService analyticsDataService) {
        this.analyticsDataService = analyticsDataService;
    }

    public AnalyticsResult computeAnalytics() {
        final var lastAnalyticsDive = analyticsDataService.findLatestAnalyticsDepthVarianceDiveId();
        final var divesSinceLast =
                analyticsDataService.findAllDivesSince(lastAnalyticsDive, PageRequest.of(0, 100));
        final var result =
                divesSinceLast.result().stream()
                        .map(this::computeAnalytics)
                        .reduce(AnalyticsResult::merge)
                        .orElse(new AnalyticsResult(true, List.of()));
        if (divesSinceLast.totalPages() <= 1) {
            return result;
        }
        logger.debug(
                "Got {} pages of {} dives each, only processed the first page.",
                divesSinceLast.totalPages(),
                divesSinceLast.pageSize());
        return result.merge(
                new AnalyticsResult(
                        false, List.of("There are more than " + MAX_DIVES_PER_RUN + " dives.")));
    }

    private AnalyticsResult computeAnalytics(final Dive dive) {
        final var splits = createSegments(dive);
        final var _ = splits.stream().map(this::createAnalytics).map(analyticsDataService::save);
        return new AnalyticsResult(true, List.of());
    }

    private Collection<DiveProfileSegment> createSegments(final Dive dive) {
        return dive.profiles().stream().flatMap(this::createSegments).toList();
    }

    private Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        return Stream.of(new DiveProfileSegment(profile, 0, profile.measurements()));
    }

    private AnalyticsDepthVariance createAnalytics(final DiveProfileSegment segment) {
        final var depthByTime =
                segment.measurements().stream()
                        .map(DiveMeasurementWithId::measurement)
                        .map(m -> Pair.of(m.time().toEpochMilli(), m.depth()))
                        .sorted(Comparator.comparing(Pair::getLeft))
                        .toList();
        final var depthBySecond = getDepthByTime(depthByTime, 1000);
        final var avgMinMax = DoubleStream.of(depthBySecond).summaryStatistics();
        final var avg = avgMinMax.getAverage();
        final var sortedDeviations =
                DoubleStream.of(depthBySecond).map(d -> Math.abs(d - avg)).sorted().toArray();
        final var count = sortedDeviations.length;
        final var deviationStats = Stats.of(sortedDeviations);
        return new AnalyticsDepthVariance(
                segment.profile(),
                segment.measurements().getFirst(),
                segment.measurements().getLast(),
                avg,
                avgMinMax.getMax(),
                avgMinMax.getMin(),
                deviationStats.mean(),
                deviationStats.populationVariance(),
                sortedDeviations[count / 100],
                sortedDeviations[count / 10],
                sortedDeviations[count / 2],
                sortedDeviations[count - count / 10],
                sortedDeviations[count - count / 100]);
    }

    private static double[] getDepthByTime(
            final List<Pair<Long, Double>> depthByTime, final long msDivider) {
        final var start = depthByTime.getFirst().getLeft();
        final var startSeconds = start / msDivider;
        final var end = depthByTime.getLast().getLeft();
        final var endSeconds = end / msDivider;
        final var depthBySecond = new double[Math.toIntExact(endSeconds - startSeconds + 1)];
        var i = 0;
        for (var s = 0; s < depthBySecond.length; s++) {
            final var millis = start + s * msDivider;
            while (i < depthByTime.size() - 2 && depthByTime.get(i + 1).getLeft() <= millis) {
                i++;
            }
            depthBySecond[i] = getDepth(depthByTime, i, millis);
        }
        return depthBySecond;
    }

    private static double getDepth(
            final List<Pair<Long, Double>> depthByTime, final int i, final long millis) {
        final var before = depthByTime.get(i);
        final var after = depthByTime.get(i + 1);

        final var dt = after.getLeft() - before.getLeft();
        if (dt == 0) {
            return before.getRight();
        }
        if (dt == 1) {
            return after.getRight();
        }
        final var fraction = (double) (millis - before.getLeft()) / dt;
        // (1 - fraction) * before + fraction * after
        return before.getRight() + fraction * (after.getRight() - before.getRight());
    }
}
