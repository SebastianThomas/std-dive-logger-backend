package ch.sthomas.stddivelogger.analytics.job;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsJobs {
    private final AnalyticsJobQueue queue;

    public AnalyticsJobs(final AnalyticsJobQueue queue) {
        this.queue = queue;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void computeAnalytics() {
        queue.enqueue(JobKind.PROFILES, false);
    }

    @Schedules({
        @Scheduled(cron = "0 0 3 * * *", zone = "UTC"),
        @Scheduled(initialDelay = 10000),
    })
    public void computeDiveSummaries() {
        queue.enqueue(JobKind.SUMMARIES, false);
    }

    /** Refresh cached home-dashboard activity/trend stats for divers whose dives changed. */
    @Schedules({
        @Scheduled(cron = "30 * * * * *", zone = "UTC"),
        @Scheduled(initialDelay = 15000),
    })
    public void recomputeDiverActivityStats() {
        queue.enqueue(JobKind.ACTIVITY, false);
    }

    /**
     * Recompute stored reminders (dive anniversaries + the dynamic "dive again" nudge). Runs often
     * because "today" moves at midnight - every active diver needs a fresh set each day, walked
     * through in batches.
     */
    @Schedules({
        @Scheduled(cron = "0 */5 * * * *", zone = "UTC"),
        @Scheduled(initialDelay = 20000),
    })
    public void recomputeDiverReminders() {
        queue.enqueue(JobKind.REMINDERS, false);
    }

    /** Web-push the reminders that are due and not yet pushed. */
    @Schedules({
        @Scheduled(cron = "0 2/5 * * * *", zone = "UTC"),
        @Scheduled(initialDelay = 45000),
    })
    public void sendDueReminderPushes() {
        queue.enqueue(JobKind.PUSH, false);
    }

    /** Nightly cleanup of long-expired reminder rows. */
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void purgeExpiredReminders() {
        queue.enqueue(JobKind.CLEANUP, false);
    }

    /** Bulk-refresh the global per-site aggregates behind "suggest a dive site". */
    @Schedules({
        @Scheduled(cron = "0 4/15 * * * *", zone = "UTC"),
        @Scheduled(initialDelay = 30000),
    })
    public void refreshDiveSiteStats() {
        queue.enqueue(JobKind.SITES, false);
    }
}
