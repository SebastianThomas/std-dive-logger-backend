package ch.sthomas.stddivelogger.autocomplete;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        exclude = {
            org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration
                    .class
        },
        scanBasePackages = {
            "ch.sthomas.stddivelogger.data.observability",
            "ch.sthomas.stddivelogger.autocomplete",
            "ch.sthomas.stddivelogger.utils.advice",
        })
public class StdDiveLoggerAutocompleteApplication {

    static void main(final String[] args) {
        SpringApplication.run(StdDiveLoggerAutocompleteApplication.class, args);
    }
}
