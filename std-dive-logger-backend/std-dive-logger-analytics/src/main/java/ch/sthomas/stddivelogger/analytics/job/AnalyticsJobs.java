package ch.sthomas.stddivelogger.analytics.job;

import ch.sthomas.stddivelogger.analytics.services.AnalyticsService;
import ch.sthomas.stddivelogger.model.exception.AnalyticsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsJobs {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobs.class);
    private final AnalyticsService analyticsService;

    public AnalyticsJobs(final AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void computeAnalytics() {
        final var result = analyticsService.computeAnalytics();
        if (!result.successful()) {
            throw new AnalyticsException(result);
        }
    }

    @Schedules({
        @Scheduled(cron = "0 0 3 * * *"),
        @Scheduled(initialDelay = 10000),
    })
    public void computeDiveSummaries() {
        analyticsService.computeDiveSummaries();
    }

    /** Refresh cached home-dashboard activity/trend stats for divers whose dives changed. */
    @Schedules({
        @Scheduled(cron = "30 * * * * *"),
        @Scheduled(initialDelay = 15000),
    })
    public void recomputeDiverActivityStats() {
        analyticsService.recomputeDiverActivityStats();
    }

    /**
     * Recompute stored reminders (dive anniversaries + the dynamic "dive again" nudge). Runs often
     * because "today" moves at midnight - every active diver needs a fresh set each day, walked
     * through in batches.
     */
    @Schedules({
        @Scheduled(cron = "0 */5 * * * *"),
        @Scheduled(initialDelay = 20000),
    })
    public void recomputeDiverReminders() {
        analyticsService.recomputeDiverReminders();
    }

    /** Web-push the reminders that are due and not yet pushed (TODO: real sender). */
    @Schedules({
        @Scheduled(cron = "0 2/5 * * * *"),
        @Scheduled(initialDelay = 45000),
    })
    public void sendDueReminderPushes() {
        analyticsService.sendDueReminderPushes();
    }

    /** Nightly cleanup of long-expired reminder rows. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredReminders() {
        analyticsService.purgeExpiredReminders();
    }
}
