package ch.sthomas.stddivegastracker.ws.auth;

import ch.sthomas.stddivegastracker.service.UserService;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies dive-logger access tokens (API.md "Auth"): EdDSA (Ed25519) signature by one of the
 * configured public keys - selected by the {@code kid} header, the first 16 characters of
 * base64url(SHA-256(X.509 DER of the key)) - plus {@code iss}, {@code exp}, an email {@code sub}, a
 * numeric {@code uid} and, when required, {@code email_verified}. Only the public keys are here, so
 * tokens can be checked but never minted. Under dev-login the ephemeral dev key is accepted as
 * well, and only it may carry a negative {@code uid}. Any failure is a {@link BadJwtException}
 * (401), never anything else.
 */
public final class DiveLoggerJwtDecoder implements JwtDecoder {

    public static final String UID_CLAIM = "uid";
    public static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    private static final String ALGORITHM = "EdDSA";
    private static final int KEY_ID_LENGTH = 16;
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final Map<String, PublicKey> keys = new LinkedHashMap<>();
    private final @Nullable PublicKey soleKey;
    private final @Nullable String devKeyId;
    private final boolean requireEmailVerifiedClaim;
    private final JwtParser parser;

    /**
     * @param publicKeyPems PEM X.509 Ed25519 public keys (real newlines or literal {@code \n})
     * @param devKey the dev-login key, or null
     * @throws IllegalStateException on an unparsable key or when no key at all is available
     */
    public DiveLoggerJwtDecoder(
            final List<String> publicKeyPems,
            final String issuer,
            final boolean requireEmailVerifiedClaim,
            final @Nullable PublicKey devKey) {
        for (var i = 0; i < publicKeyPems.size(); i++) {
            final var key = parsePublicKey(publicKeyPems.get(i), i);
            keys.put(keyId(key), key);
        }
        if (devKey != null) {
            devKeyId = keyId(devKey);
            keys.put(devKeyId, devKey);
        } else {
            devKeyId = null;
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                    "No JWT verification key: set ch.sthomas.stddivegastracker.auth.public-keys to"
                            + " the dive logger's PEM Ed25519 public key(s), or run with profile"
                            + " dev-login.");
        }
        // A token without kid is only unambiguous with exactly one (real) key.
        soleKey = devKey == null && keys.size() == 1 ? keys.values().iterator().next() : null;
        this.requireEmailVerifiedClaim = requireEmailVerifiedClaim;
        this.parser =
                Jwts.parser()
                        .keyLocator(this::locate)
                        .requireIssuer(issuer)
                        .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                        .build();
    }

    /** The {@code kid} of a key, as the dive logger computes it (task B0). */
    public static String keyId(final PublicKey key) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256(key.getEncoded()))
                .substring(0, KEY_ID_LENGTH);
    }

    static byte[] sha256(final byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JDK.", e);
        }
    }

    @Override
    public Jwt decode(final String token) {
        try {
            return verify(token);
        } catch (final BadJwtException e) {
            throw e;
        } catch (final RuntimeException e) {
            // Untrusted input: whatever the parser or a claim cast throws is just a bad token.
            throw new BadJwtException("Invalid access token.", e);
        }
    }

    private Jwt verify(final String token) {
        final var jws = parser.parseSignedClaims(token);
        // The key type already pins EdDSA (jjwt rejects HS*/RS*/none for an Ed25519 key); be
        // explicit.
        if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
            throw new BadJwtException("Invalid access token.");
        }
        final var claims = jws.getPayload();
        final var subject = claims.getSubject();
        final var expiration = claims.getExpiration();
        if (subject == null || expiration == null) {
            throw new BadJwtException("Access token lacks a subject or an expiry.");
        }
        UserService.normalizeEmail(subject);
        final var userId = userId(claims.get(UID_CLAIM));
        // Real dive-logger ids are positive; negative ones belong to the dev-login key alone.
        final var fromDevKey = devKeyId != null && devKeyId.equals(jws.getHeader().getKeyId());
        if (fromDevKey ? userId >= 0 : userId <= 0) {
            throw new BadJwtException("Access token has no valid user id.");
        }
        final var emailVerified = claims.get(EMAIL_VERIFIED_CLAIM, Boolean.class);
        if (requireEmailVerifiedClaim && !Boolean.TRUE.equals(emailVerified)) {
            throw new BadJwtException("Email address not verified.");
        }
        final var builder =
                Jwt.withTokenValue(token)
                        .headers(headers -> headers.putAll(jws.getHeader()))
                        .subject(subject)
                        .expiresAt(expiration.toInstant())
                        .claim(UID_CLAIM, userId)
                        .claim(EMAIL_VERIFIED_CLAIM, Boolean.TRUE.equals(emailVerified));
        final var issuedAt = claims.getIssuedAt();
        if (issuedAt != null) {
            builder.issuedAt(issuedAt.toInstant());
        }
        return builder.build();
    }

    private Key locate(final Header header) {
        final var keyId =
                header instanceof ProtectedHeader protectedHeader
                        ? protectedHeader.getKeyId()
                        : null;
        final var key = keyId == null ? soleKey : keys.get(keyId);
        if (key == null) {
            throw new JwtException("No verification key for this access token.");
        }
        return key;
    }

    /** A whole JSON number; whatever number type the JSON library produced. */
    private static long userId(final @Nullable Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).longValueExact();
        }
        throw new BadJwtException("Access token has no valid user id.");
    }

    private static PublicKey parsePublicKey(final String pem, final int index) {
        final var base64 =
                pem.replace("\\n", "\n")
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (final IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException(
                    "ch.sthomas.stddivegastracker.auth.public-keys["
                            + index
                            + "] is not a PEM-encoded Ed25519 public key (X.509 SubjectPublicKeyInfo).",
                    e);
        }
    }
}
