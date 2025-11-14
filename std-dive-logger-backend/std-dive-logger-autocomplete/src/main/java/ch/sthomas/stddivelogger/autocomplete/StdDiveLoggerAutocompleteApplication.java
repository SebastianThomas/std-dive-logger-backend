package ch.sthomas.stddivelogger.autocomplete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.zalando.problem.spring.web.autoconfigure.security.ProblemSecurityAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data",
            "ch.sthomas.stddivelogger.service",
                "ch.sthomas.stddivelogger.autocomplete",
        },
        // exclude ErrorMvcAutoConfiguration and ProblemSecurityAutoConfiguration when using
        // zalando/problem
        exclude = {ErrorMvcAutoConfiguration.class, ProblemSecurityAutoConfiguration.class})
public class StdDiveLoggerAutocompleteApplication {

    public static void main(String[] args) {
        SpringApplication.run(StdDiveLoggerAutocompleteApplication.class, args);
    }
}
