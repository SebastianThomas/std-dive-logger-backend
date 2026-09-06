package ch.sthomas.stddivelogger.analytics.job;

import ch.sthomas.stddivelogger.analytics.services.AnalyticsService;
import ch.sthomas.stddivelogger.data.service.AnalyticsJobRunStore;
import ch.sthomas.stddivelogger.model.exception.AnalyticsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

import javax.sql.DataSource;

@Service
public class AnalyticsJobQueue {
    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsJobQueue.class);
    private static final long LOCK_ID = 734268193;
    private final DataSource dataSource;
    private final AnalyticsJobRunStore runs;
    private final AnalyticsService analytics;

    public AnalyticsJobQueue(
            DataSource dataSource, AnalyticsJobRunStore runs, AnalyticsService analytics) {
        this.dataSource = dataSource;
        this.runs = runs;
        this.analytics = analytics;
    }

    public boolean enqueue(final JobKind job, final boolean manual) {
        return runs.enqueue(job.name(), manual ? "MANUAL" : "SCHEDULED");
    }

    @Scheduled(fixedDelay = 1000)
    public void processNext() throws SQLException {
        try (final var connection = dataSource.getConnection();
                final var statement = connection.createStatement()) {
            try (final var result =
                    statement.executeQuery("SELECT pg_try_advisory_lock(" + LOCK_ID + ")")) {
                result.next();
                if (!result.getBoolean(1)) return;
            }
            try {
                final var run = runs.claimNext();
                if (run == null) return;
                try {
                    execute(JobKind.valueOf(run.job()));
                    runs.finish(run.id(), null);
                    LOG.info("Analytics job {} run {} completed", run.job(), run.id());
                } catch (RuntimeException exception) {
                    runs.finish(run.id(), exception.getClass().getSimpleName());
                    LOG.error("Analytics job {} run {} failed", run.job(), run.id(), exception);
                }
            } finally {
                statement.execute("SELECT pg_advisory_unlock(" + LOCK_ID + ")");
            }
        }
    }

    private void execute(final JobKind job) {
        switch (job) {
            case PROFILES -> {
                final var result = analytics.computeAnalytics();
                if (!result.successful()) throw new AnalyticsException(result);
            }
            case SUMMARIES -> analytics.computeDiveSummaries();
            case ACTIVITY -> analytics.recomputeDiverActivityStats();
            case REMINDERS -> analytics.recomputeDiverReminders();
            case PUSH -> analytics.sendDueReminderPushes();
            case CLEANUP -> analytics.purgeExpiredReminders();
            case SITES -> analytics.refreshDiveSiteStats();
        }
    }
}
