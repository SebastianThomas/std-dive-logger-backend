package ch.sthomas.stddivelogger.ws.auth;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

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
            RefreshTokenRepository refreshTokenRepository) {
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

    public String generateToken(final String username, final TokenType tokenType) {
        final var expiration =
                switch (tokenType) {
                    case ACCESS_TOKEN -> ofHours(1);
                    case REFRESH_TOKEN -> ofDays(30);
                };
        final var issuedAt = new Date();
        final var expirationAt = new Date(System.currentTimeMillis() + expiration.toMillis());
        final var builder =
                Jwts.builder().subject(username).issuedAt(issuedAt).expiration(expirationAt);
        if (tokenType == TokenType.ACCESS_TOKEN) {
            final var jti = UUID.randomUUID().toString();
            builder.id(jti);
            refreshTokenRepository.save(new RefreshTokenEntity(jti, expirationAt.toInstant()));
        }
        return builder.signWith(getSigningKey(tokenType)).compact();
    }

    public String extractUsername(final String token, final TokenType tokenType) {
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
        if (!extractedUsername.equals(username) || isTokenExpired(token, tokenType)) {
            return false;
        }
        final var jti = extractJtiFromRefreshToken(token);
        return refreshTokenRepository.existsByJtiAndExpiresAtAfter(jti, OffsetDateTime.now());
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
