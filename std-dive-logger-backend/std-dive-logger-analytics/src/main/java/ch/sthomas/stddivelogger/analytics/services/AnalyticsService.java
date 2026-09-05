package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.DiveSiteStatsDataService;
import ch.sthomas.stddivelogger.data.service.DiverActivityStatsDataService;
import ch.sthomas.stddivelogger.data.service.DiverReminderDataService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceStats;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsResult;
import ch.sthomas.stddivelogger.model.analytics.DiveGasCalculator;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.home.ReminderKind;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.push.WebPushMessage;
import ch.sthomas.stddivelogger.service.PushService;

import com.google.common.math.Stats;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.DoubleStream;

@Service
public class AnalyticsService {
    // Bumped to 2 to pick up backend-computed PO2/FO2 (DiveGasCalculator) for every already
    // existing dive, not just newly-imported ones.
    public static final long ANALYTICS_VERSION = 2;
    // Identifies this recomputation job's state in AnalyticsJobState, per dive, so a bumped
    // ANALYTICS_VERSION tells us exactly which dives are stale instead of reprocessing
    // everything (or, worse, silently reprocessing nothing).
    public static final String JOB_MODULE = "analytics";
    public static final String JOB_NAME = "dive-profile-segments";
    private static final int MAX_DIVES_PER_RUN = 100;
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    private static final int MAX_ACTIVITY_STATS_DIVERS_PER_RUN = 200;
    private static final int MAX_REMINDER_DIVERS_PER_RUN = 200;

    private final AnalyticsDataService analyticsDataService;
    private final AnalyticsSegmentService analyticsSegmentService;
    private final DiveDataService diveDataService;
    private final DiverActivityStatsDataService diverActivityStatsDataService;
    private final DiverReminderDataService diverReminderDataService;
    private final PushService pushService;
    private final DiveSiteStatsDataService diveSiteStatsDataService;

    public AnalyticsService(
            final AnalyticsDataService analyticsDataService,
            final AnalyticsSegmentService analyticsSegmentService,
            DiveDataService diveDataService,
            final DiverActivityStatsDataService diverActivityStatsDataService,
            final DiverReminderDataService diverReminderDataService,
            final PushService pushService,
            final DiveSiteStatsDataService diveSiteStatsDataService) {
        this.analyticsDataService = analyticsDataService;
        this.analyticsSegmentService = analyticsSegmentService;
        this.diveDataService = diveDataService;
        this.diverActivityStatsDataService = diverActivityStatsDataService;
        this.diverReminderDataService = diverReminderDataService;
        this.pushService = pushService;
        this.diveSiteStatsDataService = diveSiteStatsDataService;
    }

    /** Bulk-refreshes the global per-site aggregates behind "suggest a dive site". */
    public void refreshDiveSiteStats() {
        diveSiteStatsDataService.refreshAll();
    }

    /**
     * Recompute the cached home-dashboard {@code DiverActivityStats} for every diver whose dives
     * changed since the last run (or whose blob is missing / at an older version). One transaction
     * per diver, so one bad row can't roll back the batch.
     */
    public void recomputeDiverActivityStats() {
        final var diverIds =
                diverActivityStatsDataService.findDiverIdsNeedingRecompute(
                        MAX_ACTIVITY_STATS_DIVERS_PER_RUN);
        int done = 0;
        for (final var diverId : diverIds) {
            try {
                diverActivityStatsDataService.computeAndStore(diverId);
                done++;
            } catch (final RuntimeException e) {
                logger.warn("Failed to recompute activity stats for diver {}", diverId, e);
            }
        }
        if (done > 0) {
            logger.info("Recomputed activity stats for {} diver(s).", done);
        }
    }

    /**
     * Recompute stored reminders for divers whose set is stale (day rolled over or dives changed).
     */
    public void recomputeDiverReminders() {
        final var diverIds =
                diverReminderDataService.findDiverIdsNeedingRecompute(MAX_REMINDER_DIVERS_PER_RUN);
        int done = 0;
        for (final var diverId : diverIds) {
            try {
                diverReminderDataService.computeAndStore(diverId);
                done++;
            } catch (final RuntimeException e) {
                logger.warn("Failed to recompute reminders for diver {}", diverId, e);
            }
        }
        if (done > 0) {
            logger.info("Recomputed reminders for {} diver(s).", done);
        }
    }

    /** Web-pushes reminders that are due and not yet pushed; marks them pushed either way. */
    public void sendDueReminderPushes() {
        final var due = diverReminderDataService.findDuePushes();
        int pushed = 0;
        for (final var reminder : due) {
            try {
                final var message =
                        new WebPushMessage(
                                reminder.title(),
                                reminder.body(),
                                targetUrl(reminder),
                                reminder.kind().name());
                pushService.sendToUser(reminder.userId(), message);
                diverReminderDataService.markPushed(reminder.reminderId());
                pushed++;
            } catch (final RuntimeException e) {
                logger.warn("Failed to push reminder {}", reminder.reminderId(), e);
            }
        }
        if (pushed > 0) {
            logger.info(
                    "Processed {} due reminder push(es) (push {}).",
                    pushed,
                    pushService.isEnabled() ? "enabled" : "not configured");
        }
    }

    private static String targetUrl(final DiverReminderDataService.PushableReminder reminder) {
        if (reminder.kind() == ReminderKind.DIVE_ANNIVERSARY && reminder.diveId() != null) {
            return "/dives/view/" + reminder.diveId();
        }
        if (reminder.kind() == ReminderKind.DIVE_AGAIN_NUDGE) {
            return "/dives/map";
        }
        return "/";
    }

    /** Nightly: drop reminders that expired a couple of days ago. */
    public void purgeExpiredReminders() {
        final int deleted = diverReminderDataService.purgeExpired();
        if (deleted > 0) {
            logger.info("Purged {} expired reminder(s).", deleted);
        }
    }

    public AnalyticsResult computeAnalytics() {
        final var candidates =
                analyticsDataService.findDivesNeedingRecompute(
                        JOB_MODULE, JOB_NAME, ANALYTICS_VERSION, MAX_DIVES_PER_RUN);
        final var result =
                candidates.dives().stream()
                        .map(this::computeAnalytics)
                        .reduce(AnalyticsResult::merge)
                        .orElse(new AnalyticsResult(true, List.of()));
        if (!candidates.hasMore()) {
            if (!candidates.dives().isEmpty()) {
                logger.debug("Finished computing {} analytics.", candidates.dives().size());
            }
            return result;
        }
        logger.info(
                "More than {} dives need recomputing, only processed the first batch.",
                MAX_DIVES_PER_RUN);
        return result.merge(
                new AnalyticsResult(
                        false, List.of("There are more than " + MAX_DIVES_PER_RUN + " dives.")));
    }

    private AnalyticsResult computeAnalytics(final Dive dive) {
        // Delete-and-replace: otherwise a recompute (e.g. after a version bump) would just pile
        // new segment/depth-variance rows up next to the stale ones instead of replacing them.
        analyticsDataService.deleteExistingSegmentsAndAnalytics(dive.id());
        final var splits = createSegments(dive);
        final var analytics = splits.stream().map(this::createAnalytics).toList();
        final var savedAnalytics = analyticsDataService.saveAll(analytics);
        // Computed from the dive's own profiles (each with measurements) rather than from the
        // just-created segments, since it needs to reason across every profile together - see
        // DiveGasCalculator's own docs for why (a bailout on one profile affects every profile's
        // calculated values, not just the one that logged it).
        final var gasResults = DiveGasCalculator.calculate(dive.profiles());
        analyticsDataService.saveGasResults(gasResults);
        analyticsDataService.recordJobState(
                dive.id(), JOB_MODULE, JOB_NAME, ANALYTICS_VERSION, Instant.now());
        return new AnalyticsResult(
                true,
                List.of(
                        MessageFormat.format(
                                "Saved {0} analytics, {1} gas points",
                                savedAnalytics.size(), gasResults.size())));
    }

    private List<DiveProfileSegmentWithId> createSegments(final Dive dive) {
        return dive.profiles().stream()
                .flatMap(analyticsSegmentService::createSegments)
                .map(analyticsDataService::saveSegment)
                .toList();
    }

    // Package-private (not private) so AnalyticsServiceTest can exercise it directly without a
    // full Dive/segment-service fixture for every case.
    AnalyticsDepthVariance createAnalytics(final DiveProfileSegmentWithId segmentWithId) {
        final var segment = segmentWithId.segment();
        Objects.requireNonNull(segment, "Segment must not be null");
        final var measurements =
                Objects.requireNonNull(
                        segment.measurements(), "Segment Measurements must not be null");
        if (measurements.isEmpty()) {
            logger.info(
                    "Empty segment for profile: {} with start index {}",
                    segment.profile().id(),
                    segment.firstMeasurementIdx());
            throw new IllegalArgumentException("Empty segment");
        }
        // A NaN/Infinity depth (e.g. surviving from an old buggy import) would otherwise poison
        // every stat computed below - summaryStatistics(), the deviation math, all of it - for
        // the whole segment. Same guard DiveGasCalculator.calculate applies for the same reason.
        final var finiteMeasurements =
                measurements.stream()
                        .map(DiveMeasurementWithId::measurement)
                        .filter(m -> Double.isFinite(m.depth()))
                        .toList();
        if (finiteMeasurements.isEmpty()) {
            logger.info(
                    "No finite-depth measurements in segment for profile: {} with start index {}",
                    segment.profile().id(),
                    segment.firstMeasurementIdx());
            throw new IllegalArgumentException("No finite-depth measurements in segment");
        }
        if (finiteMeasurements.size() == 1) {
            return new AnalyticsDepthVariance(
                    segmentWithId,
                    new AnalyticsDepthVarianceStats(
                            ANALYTICS_VERSION, finiteMeasurements.getFirst().depth()));
        }
        final var depthByTime =
                finiteMeasurements.stream()
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
        final long start = depthByTime.getFirst().getLeft();
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

    public void computeDiveSummaries() {
        diveDataService.computeMissingDiveSummaries();
    }
}
