package ch.sthomas.stddivegastracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Scans only its own package tree - never ch.sthomas.stddivelogger.* (see AGENTS.md "Isolation").
@SpringBootApplication
@ConfigurationPropertiesScan
public class StdDiveGasTrackerApplication {

    static void main(final String[] args) {
        SpringApplication.run(StdDiveGasTrackerApplication.class, args);
    }
}
