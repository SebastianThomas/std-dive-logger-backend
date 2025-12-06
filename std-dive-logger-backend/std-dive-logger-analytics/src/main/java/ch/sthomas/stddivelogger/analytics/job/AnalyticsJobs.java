package ch.sthomas.stddivelogger.analytics.job;

import ch.sthomas.stddivelogger.analytics.services.AnalyticsService;
import ch.sthomas.stddivelogger.model.exception.AnalyticsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AnalyticsJobs {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobs.class);
    private final AnalyticsService analyticsService;

    public AnalyticsJobs(final AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void computeAnalytics() {
        logger.info("Computing analytics at {}", Instant.now());
        final var result = analyticsService.computeAnalytics();
        if (!result.successful()) {
            throw new AnalyticsException(result);
        }
    }
}
