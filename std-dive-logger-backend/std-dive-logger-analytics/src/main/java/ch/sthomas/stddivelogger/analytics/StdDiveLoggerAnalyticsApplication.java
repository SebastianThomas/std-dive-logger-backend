package ch.sthomas.stddivelogger.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
            "ch.sthomas.stddivelogger.analytics",
            "ch.sthomas.stddivelogger.utils.advice",
        })
public class StdDiveLoggerAnalyticsApplication {

    static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerAnalyticsApplication.class, args);
    }
}
