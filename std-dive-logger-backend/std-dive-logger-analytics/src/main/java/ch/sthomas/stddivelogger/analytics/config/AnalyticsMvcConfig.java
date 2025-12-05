package ch.sthomas.stddivelogger.analytics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AnalyticsMvcConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public AnalyticsMvcConfig(
            @Value("${ch.sthomas.stddivelogger.autocomplete.cors.allowed_origins:}")
                    final String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON) // default to JSON
                .favorParameter(false)
                .ignoreAcceptHeader(false);
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(allowedOrigins);
    }
}
