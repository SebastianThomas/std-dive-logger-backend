package ch.sthomas.stddivelogger.analytics.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Tests set scheduling.enabled=false (see @SpringBootTest(properties = ...) on each integration
// test) - a scheduled job can otherwise fire mid-test and throw once Testcontainers starts
// tearing down the DB connection pool at context shutdown. No test relies on a @Scheduled method
// actually firing on its own schedule; they all call the job's method directly.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsSchedulingConfig {}
