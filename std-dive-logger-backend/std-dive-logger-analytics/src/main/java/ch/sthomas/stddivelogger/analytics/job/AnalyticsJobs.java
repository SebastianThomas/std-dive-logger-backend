package ch.sthomas.stddivelogger.analytics.job;

import ch.sthomas.stddivelogger.analytics.services.AnalyticsService;
import ch.sthomas.stddivelogger.model.exception.AnalyticsException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsJobs {
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
}
