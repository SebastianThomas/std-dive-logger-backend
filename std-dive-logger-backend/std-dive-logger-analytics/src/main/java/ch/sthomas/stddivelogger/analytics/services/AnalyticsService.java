package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceStats;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsResult;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;

import com.google.common.math.Stats;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.DoubleStream;

@Service
public class AnalyticsService {
    public static final long ANALYTICS_VERSION = 1;
    private static final long MAX_DIVES_PER_RUN = 100;
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsDataService analyticsDataService;
    private final AnalyticsSegmentService analyticsSegmentService;

    public AnalyticsService(
            final AnalyticsDataService analyticsDataService,
            final AnalyticsSegmentService analyticsSegmentService) {
        this.analyticsDataService = analyticsDataService;
        this.analyticsSegmentService = analyticsSegmentService;
    }

    public AnalyticsResult computeAnalytics() {
        final var lastAnalyticsDive =
                analyticsDataService.findLatestAnalyticsDepthVarianceDiveId(ANALYTICS_VERSION);
        final var divesSinceLast =
                analyticsDataService.findAllDivesSince(lastAnalyticsDive, PageRequest.of(0, 100));
        final var result =
                divesSinceLast.result().stream()
                        .map(this::computeAnalytics)
                        .reduce(AnalyticsResult::merge)
                        .orElse(new AnalyticsResult(true, List.of()));
        if (divesSinceLast.totalPages() <= 1) {
            if (divesSinceLast.totalPages() == 1) {
                logger.debug("Finished computing {} analytics.", divesSinceLast.result().size());
            }
            return result;
        }
        logger.info(
                "Got {} pages of {} dives each, only processed the first page.",
                divesSinceLast.totalPages(),
                divesSinceLast.pageSize());
        return result.merge(
                new AnalyticsResult(
                        false, List.of("There are more than " + MAX_DIVES_PER_RUN + " dives.")));
    }

    private AnalyticsResult computeAnalytics(final Dive dive) {
        final var splits = createSegments(dive);
        final var analytics = splits.stream().map(this::createAnalytics).toList();
        final var savedAnalytics = analyticsDataService.saveAll(analytics);
        return new AnalyticsResult(
                true, List.of(MessageFormat.format("Saved {0} analytics", savedAnalytics.size())));
    }

    private List<DiveProfileSegmentWithId> createSegments(final Dive dive) {
        return dive.profiles().stream()
                .flatMap(analyticsSegmentService::createSegments)
                .map(analyticsDataService::saveSegment)
                .toList();
    }

    private AnalyticsDepthVariance createAnalytics(final DiveProfileSegmentWithId segmentWithId) {
        final var segment = segmentWithId.segment();
        Objects.requireNonNull(segment, "Segment must not be null");
        Objects.requireNonNull(segment.measurements(), "Segment Measurements must not be null");
        if (segment.measurements().isEmpty()) {
            logger.info(
                    "Empty segment for profile: {} with start index {}",
                    segment.profile().id(),
                    segment.firstMeasurementIdx());
            throw new IllegalArgumentException("Empty segment");
        }
        if (segment.measurements().size() == 1) {
            return new AnalyticsDepthVariance(
                    segmentWithId,
                    new AnalyticsDepthVarianceStats(
                            ANALYTICS_VERSION,
                            segment.measurements().getFirst().measurement().depth()));
        }
        final var depthByTime =
                segment.measurements().stream()
                        .map(DiveMeasurementWithId::measurement)
                        .map(m -> Pair.of(m.time().toEpochMilli(), m.depth()))
                        .sorted(Comparator.comparing(Pair::getLeft))
                        .toList();
        final var depthBySecond = getDepthByTime(depthByTime, 1000);
        if (depthBySecond.length < 2) {
            logger.info("Depth by second should have at least two entries, got {}.", depthBySecond);
        }
        final var avgMinMax = DoubleStream.of(depthBySecond).summaryStatistics();
        final var min = avgMinMax.getMin();
        final var max = avgMinMax.getMax();
        final var avg = avgMinMax.getAverage();
        final var sortedDeviations =
                DoubleStream.of(depthBySecond).map(d -> Math.abs(d - avg)).sorted().toArray();
        final var count = sortedDeviations.length;
        final var deviationStats = Stats.of(sortedDeviations);
        final var ninetyNinthPercentileIdx = Math.min(count - count / 100, count - 1);
        final var ninetiethPercentileIdx = Math.min(count - count / 10, count - 1);
        return new AnalyticsDepthVariance(
                segmentWithId,
                new AnalyticsDepthVarianceStats(
                        ANALYTICS_VERSION,
                        avg,
                        max,
                        min,
                        deviationStats.mean(),
                        deviationStats.populationVariance(),
                        sortedDeviations[count / 100],
                        sortedDeviations[count / 10],
                        sortedDeviations[count / 2],
                        sortedDeviations[ninetiethPercentileIdx],
                        sortedDeviations[ninetyNinthPercentileIdx]));
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
            depthBySecond[s] = getDepth(depthByTime, i, millis);
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
