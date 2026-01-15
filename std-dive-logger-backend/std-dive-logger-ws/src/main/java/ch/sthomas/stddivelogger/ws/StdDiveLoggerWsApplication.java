package ch.sthomas.stddivelogger.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.zalando.problem.spring.web.autoconfigure.security.ProblemSecurityAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
            "ch.sthomas.stddivelogger.ws",
            "ch.sthomas.stddivelogger.utils.advice",
        },
        // exclude ErrorMvcAutoConfiguration and ProblemSecurityAutoConfiguration when using
        // zalando/problem
        exclude = {ErrorMvcAutoConfiguration.class, ProblemSecurityAutoConfiguration.class})
public class StdDiveLoggerWsApplication {

    public static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerWsApplication.class, args);
    }
}
