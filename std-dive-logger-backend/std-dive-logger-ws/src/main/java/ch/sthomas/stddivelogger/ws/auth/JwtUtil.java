package ch.sthomas.stddivelogger.ws.auth;

import static java.time.Duration.*;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final String secret;
    private final String refreshSecret;
    private final RefreshTokenRepository refreshTokenRepository;

    public enum TokenType {
        ACCESS_TOKEN,
        REFRESH_TOKEN
    }

    public JwtUtil(
            @Value("${ch.sthomas.stddivelogger.ws.jwt-secret}") final String secret,
            @Value("${ch.sthomas.stddivelogger.ws.jwt-refresh-secret}") final String refreshSecret,
            final RefreshTokenRepository refreshTokenRepository) {
        this.secret = secret;
        this.refreshSecret = refreshSecret;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    private SecretKey getSigningKey(final TokenType tokenType) {
        final var key =
                switch (tokenType) {
                    case ACCESS_TOKEN -> secret;
                    case REFRESH_TOKEN -> refreshSecret;
                };
        final var keyBytes = key.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public static final Duration ACCESS_TOKEN_LIFETIME = ofSeconds(300);
    public static final Duration REFRESH_TOKEN_LIFETIME = ofDays(30);

    public String generateAccessToken(final String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_LIFETIME.toMillis()))
                .signWith(getSigningKey(TokenType.ACCESS_TOKEN))
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
                .signWith(getSigningKey(TokenType.REFRESH_TOKEN))
                .compact();
    }

    public @Nullable String extractUsername(final String token, final TokenType tokenType)
            throws JwtException {
        return Jwts.parser()
                .verifyWith(getSigningKey(tokenType))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extractJtiFromRefreshToken(final String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey(TokenType.REFRESH_TOKEN))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getId();
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
        return Jwts.parser()
                .verifyWith(getSigningKey(tokenType))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }
}
