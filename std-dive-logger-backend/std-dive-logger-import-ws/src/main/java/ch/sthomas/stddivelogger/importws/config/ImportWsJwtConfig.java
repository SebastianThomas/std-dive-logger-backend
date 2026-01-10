package ch.sthomas.stddivelogger.importws.config;

import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class ImportWsJwtConfig {

    private final String secret;

    public ImportWsJwtConfig(
            @Value("${ch.sthomas.stddivelogger.ws.jwt-secret}") final String secret) {
        this.secret = secret;
    }

    @Bean
    public SecretKey signingKey() {
        final var keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
