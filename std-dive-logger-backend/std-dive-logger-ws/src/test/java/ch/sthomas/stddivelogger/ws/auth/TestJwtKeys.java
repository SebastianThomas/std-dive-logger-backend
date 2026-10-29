package ch.sthomas.stddivelogger.ws.auth;

import static java.nio.charset.StandardCharsets.US_ASCII;

import io.jsonwebtoken.Jwts;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

/**
 * ws's access-token keys in tests: one throwaway Ed25519 pair per test JVM (no key material
 * committed). {@link #register} goes into every ws {@code @DynamicPropertySource}.
 */
public final class TestJwtKeys {
    public static final KeyPair KEY_PAIR = generate();
    public static final String PRIVATE_KEY_PEM =
            pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded());
    public static final String PUBLIC_KEY_PEM =
            pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded());

    private TestJwtKeys() {}

    public static void register(final DynamicPropertyRegistry registry) {
        registry.add(JwtUtil.SIGNING_KEY_PROPERTY, () -> PRIVATE_KEY_PEM);
        registry.add(JwtUtil.PUBLIC_KEY_PROPERTY, () -> PUBLIC_KEY_PEM);
    }

    /** A valid access token for an existing user (ws loads the user by {@code sub}). */
    public static String accessToken(final String email) {
        return Jwts.builder()
                .header()
                .keyId(AccessTokenKeys.keyId(KEY_PAIR.getPublic()))
                .and()
                .issuer(JwtUtil.ISSUER)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.EdDSA)
                .compact();
    }

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pem(final String type, final byte[] der) {
        final var body = Base64.getMimeEncoder(64, "\n".getBytes(US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
