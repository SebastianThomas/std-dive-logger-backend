package ch.sthomas.stddivelogger.importws.config;

import ch.sthomas.stddivelogger.importws.auth.JwtUtil;

import io.jsonwebtoken.JwtParser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Only ws's public key: import-ws verifies access tokens but can never mint one. */
@Configuration
public class ImportWsJwtConfig {
    private static final String PUBLIC_KEY_PROPERTY = "ch.sthomas.stddivelogger.ws.jwt-public-key";

    private final String publicKeyPem;

    public ImportWsJwtConfig(@Value("${" + PUBLIC_KEY_PROPERTY + ":}") final String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    @Bean
    public JwtParser accessTokenParser() {
        final var hint = PUBLIC_KEY_PROPERTY + " (env STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY)";
        if (publicKeyPem.isBlank()) {
            throw new IllegalStateException(
                    hint + " is not set - expected ws's Ed25519 public key as X.509 PEM");
        }
        try {
            return JwtUtil.accessTokenParser(JwtUtil.parsePublicKey(publicKeyPem));
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(
                    hint + " is not an Ed25519 public key as X.509 PEM: " + e.getMessage(), e);
        }
    }
}
