package ch.sthomas.stddivelogger.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.zalando.problem.spring.web.autoconfigure.security.ProblemSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {
                "ch.sthomas.stddivelogger.data",
                "ch.sthomas.stddivelogger.service",
                "ch.sthomas.stddivelogger.ws",
        },
        // exclude ErrorMvcAutoConfiguration and ProblemSecurityAutoConfiguration when using zalando/problem
        exclude = {ErrorMvcAutoConfiguration.class, ProblemSecurityAutoConfiguration.class})
public class StdDiveLoggerWsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StdDiveLoggerWsApplication.class, args);
    }
}
