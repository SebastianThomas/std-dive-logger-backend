package ch.sthomas.stddivegastracker.ws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/** Browser origins of the gas-tracker web app. Bearer tokens only - no cookies, no credentials. */
@ConfigurationProperties("ch.sthomas.stddivegastracker.cors")
public record CorsProperties(@DefaultValue List<String> allowedOrigins) {}
