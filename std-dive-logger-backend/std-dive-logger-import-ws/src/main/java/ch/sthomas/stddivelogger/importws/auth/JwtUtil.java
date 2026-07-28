package ch.sthomas.stddivelogger.importws.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import javax.crypto.SecretKey;

public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private JwtUtil() {}

    public static boolean isTokenValid(
            final String token, final String username, final SecretKey signingKey) {
        final var payload =
                Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        final var extractedUsername = payload.getSubject();
        final var extractedExpiry = payload.getExpiration();
        if (!extractedUsername.equals(username) || extractedExpiry.before(new Date())) {
            logger.info("Invalid refresh token. Refresh token expired.");
            return false;
        }
        return true;
    }

    public static @Nullable String extractUsername(final String token, final SecretKey signingKey)
            throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
