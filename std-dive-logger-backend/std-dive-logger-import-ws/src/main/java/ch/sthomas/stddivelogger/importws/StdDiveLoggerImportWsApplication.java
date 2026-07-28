package ch.sthomas.stddivelogger.importws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
            "ch.sthomas.stddivelogger.importws",
            "ch.sthomas.stddivelogger.utils.advice",
        })
public class StdDiveLoggerImportWsApplication {

    public static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerImportWsApplication.class, args);
    }
}
