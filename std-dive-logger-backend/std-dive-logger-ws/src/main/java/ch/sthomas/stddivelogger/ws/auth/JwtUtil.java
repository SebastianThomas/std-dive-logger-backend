package ch.sthomas.stddivelogger.ws.auth;

import static java.time.Duration.*;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;
import ch.sthomas.stddivelogger.model.user.User;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

/**
 * Access tokens: Ed25519 (EdDSA), {@code kid} + {@code iss}/{@code sub}/{@code uid}/{@code
 * email_verified}, verifiable by import-ws and the gas tracker with the public key alone. Refresh
 * tokens: HS384 with the refresh secret, ws-internal.
 */
@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    public static final String ISSUER = "std-dive-logger";
    public static final String UID_CLAIM = "uid";
    public static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    static final String SIGNING_KEY_PROPERTY = "ch.sthomas.stddivelogger.ws.jwt-signing-key";
    static final String PUBLIC_KEY_PROPERTY = "ch.sthomas.stddivelogger.ws.jwt-public-key";

    private final PrivateKey signingKey;
    private final String keyId;
    private final JwtParser accessTokenParser;
    private final String refreshSecret;
    private final RefreshTokenRepository refreshTokenRepository;

    public enum TokenType {
        ACCESS_TOKEN,
        REFRESH_TOKEN
    }

    public JwtUtil(
            @Value("${" + SIGNING_KEY_PROPERTY + ":}") final String signingKeyPem,
            @Value("${" + PUBLIC_KEY_PROPERTY + ":}") final String publicKeyPem,
            @Value("${ch.sthomas.stddivelogger.ws.jwt-refresh-secret}") final String refreshSecret,
            final RefreshTokenRepository refreshTokenRepository) {
        this.signingKey =
                loadKey(
                        signingKeyPem,
                        SIGNING_KEY_PROPERTY,
                        "STD_DIVE_LOGGER_WS_JWT_SIGNING_KEY",
                        "an Ed25519 private key as PKCS#8 PEM",
                        AccessTokenKeys::parsePrivateKey);
        final var publicKey =
                loadKey(
                        publicKeyPem,
                        PUBLIC_KEY_PROPERTY,
                        "STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY",
                        "an Ed25519 public key as X.509 PEM",
                        AccessTokenKeys::parsePublicKey);
        if (!AccessTokenKeys.isPair(signingKey, publicKey)) {
            throw new IllegalStateException(
                    PUBLIC_KEY_PROPERTY
                            + " is not the public key of "
                            + SIGNING_KEY_PROPERTY
                            + " - configure the two halves of one pair");
        }
        this.keyId = AccessTokenKeys.keyId(publicKey);
        this.accessTokenParser = accessTokenParser(publicKey);
        this.refreshSecret = refreshSecret;
        this.refreshTokenRepository = refreshTokenRepository;
        logger.info("Access tokens signed with Ed25519 key {}", keyId);
    }

    /** EdDSA only (no HS256 or alg confusion), signed by this key, {@code iss} required. */
    public static JwtParser accessTokenParser(final PublicKey publicKey) {
        return Jwts.parser()
                .sig()
                .clear()
                .add(Jwts.SIG.EdDSA)
                .and()
                .verifyWith(publicKey)
                .requireIssuer(ISSUER)
                .build();
    }

    private static <K> K loadKey(
            final String pem,
            final String property,
            final String envVar,
            final String expected,
            final Function<String, K> parser) {
        final var hint = property + " (env " + envVar + ")";
        if (pem.isBlank()) {
            throw new IllegalStateException(
                    hint
                            + " is not set - expected "
                            + expected
                            + ", generate a pair with tools/generate-jwt-keys.sh");
        }
        try {
            return parser.apply(pem);
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(
                    hint + " is not " + expected + ": " + e.getMessage(), e);
        }
    }

    private SecretKey getRefreshKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes());
    }

    private JwtParser parser(final TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS_TOKEN -> accessTokenParser;
            case REFRESH_TOKEN -> Jwts.parser().verifyWith(getRefreshKey()).build();
        };
    }

    public static final Duration ACCESS_TOKEN_LIFETIME = ofSeconds(300);
    public static final Duration REFRESH_TOKEN_LIFETIME = ofDays(30);

    /** {@code sub} stays the email: ws and import-ws load the user by it. */
    public String generateAccessToken(final User user) {
        final var now = System.currentTimeMillis();
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .issuer(ISSUER)
                .subject(user.email())
                .claim(UID_CLAIM, user.id())
                .claim(EMAIL_VERIFIED_CLAIM, user.emailVerified())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_TOKEN_LIFETIME.toMillis()))
                .signWith(signingKey, Jwts.SIG.EdDSA)
                .compact();
    }

    /** Persists the jti with its owner, so the session can be revoked (one or all of a user's). */
    public String generateRefreshToken(final long userId, final String username) {
        final var expirationAt =
                new Date(System.currentTimeMillis() + REFRESH_TOKEN_LIFETIME.toMillis());
        final var jti = UUID.randomUUID().toString();
        refreshTokenRepository.save(new RefreshTokenEntity(jti, userId, expirationAt.toInstant()));
        return Jwts.builder()
                .subject(username)
                .id(jti)
                .issuedAt(new Date())
                .expiration(expirationAt)
                .signWith(getRefreshKey())
                .compact();
    }

    public @Nullable String extractUsername(final String token, final TokenType tokenType)
            throws JwtException {
        return parser(tokenType).parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractJtiFromRefreshToken(final String token) {
        return parser(TokenType.REFRESH_TOKEN).parseSignedClaims(token).getPayload().getId();
    }

    public boolean isTokenValid(
            final String token, final String username, final TokenType tokenType) {
        final var extractedUsername = extractUsername(token, tokenType);
        if (!username.equals(extractedUsername) || isTokenExpired(token, tokenType)) {
            logger.info("Invalid refresh token. Refresh token expired.");
            return false;
        }
        return switch (tokenType) {
            case ACCESS_TOKEN -> true;
            case REFRESH_TOKEN -> {
                final var jti = extractJtiFromRefreshToken(token);
                final var exists =
                        refreshTokenRepository.existsByJtiAndExpiresAtAfter(
                                jti, OffsetDateTime.now());
                if (!exists) {
                    logger.info("Invalid refresh token. Refresh token expired or does not exist.");
                }
                yield exists;
            }
        };
    }

    @Transactional
    public void deleteRefreshToken(final String refreshToken) {
        final var jti = extractJtiFromRefreshToken(refreshToken);
        refreshTokenRepository.deleteByJti(jti);
    }

    private boolean isTokenExpired(final String token, final TokenType tokenType) {
        return parser(tokenType)
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }
}
