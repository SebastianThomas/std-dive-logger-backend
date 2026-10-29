package ch.sthomas.stddivelogger.importws.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Verifies ws-issued access tokens with ws's Ed25519 public key only (EdDSA, {@code iss} required)
 * - import-ws holds no signing secret. Mirrors ws's {@code JwtUtil}/{@code AccessTokenKeys}.
 */
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    public static final String ISSUER = "std-dive-logger";

    private JwtUtil() {}

    /** X.509 PEM ({@code -----BEGIN PUBLIC KEY-----}, real or literal {@code \n}), Ed25519. */
    public static PublicKey parsePublicKey(final String pem) {
        final var begin = "-----BEGIN PUBLIC KEY-----";
        final var end = "-----END PUBLIC KEY-----";
        final var text = pem.replace("\\n", "\n").replace("\\r", "").strip();
        if (!text.startsWith(begin) || !text.endsWith(end)) {
            throw new IllegalArgumentException("expected a PEM block " + begin + " ... " + end);
        }
        final var body =
                text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (final InvalidKeySpecException e) {
            throw new IllegalArgumentException("not an Ed25519 X.509 public key", e);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** EdDSA only (no HS256 or alg confusion), signed by ws's key, {@code iss} required. */
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

    public static boolean isTokenValid(
            final String token, final String username, final JwtParser parser) {
        final var payload = parser.parseSignedClaims(token).getPayload();
        final var extractedUsername = payload.getSubject();
        final var extractedExpiry = payload.getExpiration();
        if (!extractedUsername.equals(username) || extractedExpiry.before(new Date())) {
            logger.info("Invalid refresh token. Refresh token expired.");
            return false;
        }
        return true;
    }

    public static @Nullable String extractUsername(final String token, final JwtParser parser)
            throws JwtException {
        return parser.parseSignedClaims(token).getPayload().getSubject();
    }
}
