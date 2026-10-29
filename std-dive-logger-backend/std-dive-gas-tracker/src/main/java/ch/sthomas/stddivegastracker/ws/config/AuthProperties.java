package ch.sthomas.stddivegastracker.ws.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Dive-logger token verification (API.md "Auth"). Parsed and checked by {@code
 * ws.auth.DiveLoggerJwtDecoder}, which fails startup when no verification key is available.
 *
 * @param publicKeys PEM X.509 Ed25519 public keys of the dive logger (real or literal {@code \n}
 *     newlines); several during a key rotation. May be empty only with the dev-login key.
 * @param issuer required {@code iss} claim
 * @param requireEmailVerifiedClaim production: true (dive-logger tokens carry {@code
 *     email_verified}, task B0)
 * @param devLoginEnabled local only, together with profile {@code dev-login}
 */
@ConfigurationProperties("ch.sthomas.stddivegastracker.auth")
public record AuthProperties(
        @DefaultValue List<String> publicKeys,
        @DefaultValue("std-dive-logger") String issuer,
        @DefaultValue("true") boolean requireEmailVerifiedClaim,
        @DefaultValue("false") boolean devLoginEnabled) {

    public AuthProperties {
        publicKeys = List.copyOf(publicKeys);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "ch.sthomas.stddivegastracker.auth.issuer must not be blank.");
        }
    }
}
