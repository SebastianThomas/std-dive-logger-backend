package ch.sthomas.stddivelogger.ws.auth;

import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

@Component
public class JwtUtil {

    private final String secret;

    public JwtUtil(@Value("${ch.sthomas.stddivelogger.ws.jwt-secret}") final String secret) {
        this.secret = secret;
    }

    private SecretKeySpec getSigningKey() {
        final var keyBytes = secret.getBytes();
        return new SecretKeySpec(keyBytes, Jwts.SIG.HS256.getId());
    }

    public String generateToken(final String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(final String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(final String token, final String username) {
        final var extractedUsername = extractUsername(token);
        return extractedUsername.equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(final String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }
}
