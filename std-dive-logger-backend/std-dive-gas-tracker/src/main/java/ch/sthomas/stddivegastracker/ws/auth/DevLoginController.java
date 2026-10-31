package ch.sthomas.stddivegastracker.ws.auth;

import ch.sthomas.stddivegastracker.service.UserService;
import ch.sthomas.stddivegastracker.ws.config.AuthProperties;

import io.jsonwebtoken.Jwts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * LOCAL DEVELOPMENT ONLY: mints an access token for any email, so agents and the UI can work
 * without a running dive logger. Needs profile {@code dev-login} AND {@code
 * ch.sthomas.stddivegastracker.auth.dev-login-enabled=true}; deployed configs set neither.
 */
@Profile("dev-login")
@ConditionalOnProperty(
        name = "ch.sthomas.stddivegastracker.auth.dev-login-enabled",
        havingValue = "true")
@RestController
@RequestMapping("/api/dev")
public final class DevLoginController {

    private static final Logger logger = LoggerFactory.getLogger(DevLoginController.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    private static final long DEV_ID_RANGE = 1_000_000_000_000L;

    private final DevLoginKey devLoginKey;
    private final String issuer;

    public DevLoginController(final DevLoginKey devLoginKey, final AuthProperties authProperties) {
        this.devLoginKey = devLoginKey;
        this.issuer = authProperties.issuer();
        logger.warn("dev-login is ENABLED: POST /api/dev/login mints tokens for any email.");
    }

    public record DevLoginRequest(@NotBlank @Email String email) {}

    public record DevLoginResponse(String accessToken) {}

    @PostMapping("/login")
    public DevLoginResponse login(@Valid @RequestBody final DevLoginRequest request) {
        final var email = UserService.normalizeEmail(request.email());
        final var now = Instant.now();
        final var token =
                Jwts.builder()
                        .header()
                        .keyId(devLoginKey.keyId())
                        .and()
                        .issuer(issuer)
                        .subject(email)
                        .claim(DiveLoggerJwtDecoder.UID_CLAIM, devUserId(email))
                        .claim(DiveLoggerJwtDecoder.EMAIL_VERIFIED_CLAIM, true)
                        .claim(
                                DiveLoggerJwtDecoder.USERNAME_CLAIM,
                                email.substring(0, email.indexOf('@')))
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(now.plus(TOKEN_LIFETIME)))
                        .signWith(devLoginKey.privateKey(), Jwts.SIG.EdDSA)
                        .compact();
        return new DevLoginResponse(token);
    }

    /**
     * Stable per email and always negative, so it never collides with a (positive) dive-logger id:
     * {@code -(1 + floorMod(first 8 bytes of SHA-256(email) as a big-endian long, 1e12))}.
     */
    static long devUserId(final String normalizedEmail) {
        final var digest =
                DiveLoggerJwtDecoder.sha256(normalizedEmail.getBytes(StandardCharsets.UTF_8));
        return -(1 + Math.floorMod(ByteBuffer.wrap(digest).getLong(), DEV_ID_RANGE));
    }
}
