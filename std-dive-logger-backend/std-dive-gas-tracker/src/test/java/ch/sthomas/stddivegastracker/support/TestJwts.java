package ch.sthomas.stddivegastracker.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Mints tokens shaped like the dive logger's (task B0): EdDSA signature, header {@code kid}, claims
 * {@code iss}, {@code sub} = email, {@code uid}, {@code email_verified}, {@code iat}, {@code exp}.
 * The key pair is a fixed TEST-ONLY one (src/test/resources/jwt/test-only-ed25519-*.pem, generated
 * with {@code openssl genpkey -algorithm ED25519}); it protects nothing and is never configured
 * outside tests. The {@code kid} is computed here independently of the production code.
 */
public final class TestJwts {

    public static final String ISSUER = "std-dive-logger";
    public static final String PUBLIC_KEY_PEM = resource("test-only-ed25519-public.pem");
    public static final PublicKey PUBLIC_KEY = publicKey(PUBLIC_KEY_PEM);
    public static final PrivateKey PRIVATE_KEY =
            privateKey(resource("test-only-ed25519-private.pem"));
    public static final String KEY_ID = keyId(PUBLIC_KEY);

    private TestJwts() {}

    /**
     * A stable positive dive-logger id for an email (so "the same person" signs in again with the
     * same uid across tests); a test that changes someone's email passes the uid explicitly.
     */
    public static long uidOf(final String email) {
        final var digest = sha256(email.getBytes(StandardCharsets.UTF_8));
        return 1 + (ByteBuffer.wrap(digest).getLong() >>> 17);
    }

    /** A valid, verified, 5-minute access token for {@code uidOf(email)}. */
    public static String accessToken(final String email) {
        return accessToken(uidOf(email), email);
    }

    public static String accessToken(final long uid, final String email) {
        return sign(builder(uid, email));
    }

    /** All claims and the {@code kid} header of a valid token; tests change what they test. */
    public static JwtBuilder builder(final @Nullable Long uid, final String email) {
        final var now = Instant.now();
        final var builder =
                Jwts.builder()
                        .header()
                        .keyId(KEY_ID)
                        .and()
                        .issuer(ISSUER)
                        .subject(email)
                        .claim("email_verified", true)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(now.plus(Duration.ofMinutes(5))));
        if (uid != null) {
            builder.claim("uid", uid);
        }
        return builder;
    }

    /** Like {@link #builder} but without any header {@code kid}. */
    public static JwtBuilder builderWithoutKeyId(final long uid, final String email) {
        final var now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .claim("uid", uid)
                .claim("email_verified", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))));
    }

    public static String sign(final JwtBuilder builder) {
        return builder.signWith(PRIVATE_KEY, Jwts.SIG.EdDSA).compact();
    }

    /** First 16 characters of base64url(SHA-256(X.509 DER)) - the token contract. */
    public static String keyId(final PublicKey key) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sha256(key.getEncoded()))
                .substring(0, 16);
    }

    private static byte[] sha256(final byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resource(final String name) {
        try (final var in = TestJwts.class.getResourceAsStream("/jwt/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] der(final String pem) {
        return Base64.getMimeDecoder()
                .decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
    }

    private static PublicKey publicKey(final String pem) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(der(pem)));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PrivateKey privateKey(final String pem) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(der(pem)));
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
