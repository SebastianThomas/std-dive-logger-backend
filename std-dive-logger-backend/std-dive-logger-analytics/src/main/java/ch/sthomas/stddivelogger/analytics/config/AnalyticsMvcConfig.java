package ch.sthomas.stddivelogger.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AnalyticsMvcConfig implements WebMvcConfigurer {

    public AnalyticsMvcConfig() {}

    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON) // default to JSON
                .favorParameter(false)
                .ignoreAcceptHeader(false);
    }
}
