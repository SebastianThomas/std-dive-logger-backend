package ch.sthomas.stddivelogger.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
            "ch.sthomas.stddivelogger.ws",
            "ch.sthomas.stddivelogger.utils.advice",
        })
public class StdDiveLoggerWsApplication {

    public static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerWsApplication.class, args);
    }
}
