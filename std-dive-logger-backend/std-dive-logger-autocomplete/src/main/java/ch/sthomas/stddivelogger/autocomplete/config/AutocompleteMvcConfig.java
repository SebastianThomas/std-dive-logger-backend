package ch.sthomas.stddivelogger.autocomplete.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AutocompleteMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON) // default to JSON
                .favorParameter(false)
                .ignoreAcceptHeader(true);
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "https://*.webdev-25.ivia.isginf.ch",
                        "https://*.sthomas.ch",
                        "http://localhost:*");
    }
}
