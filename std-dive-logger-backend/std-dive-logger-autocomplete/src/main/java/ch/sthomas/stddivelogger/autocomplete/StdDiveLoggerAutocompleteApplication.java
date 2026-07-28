package ch.sthomas.stddivelogger.autocomplete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
            "ch.sthomas.stddivelogger.autocomplete",
            "ch.sthomas.stddivelogger.utils.advice",
        })
public class StdDiveLoggerAutocompleteApplication {

    static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerAutocompleteApplication.class, args);
    }
}
