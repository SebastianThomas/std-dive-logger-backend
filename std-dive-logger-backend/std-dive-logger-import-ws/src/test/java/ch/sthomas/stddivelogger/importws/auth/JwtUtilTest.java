package ch.sthomas.stddivelogger.importws.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.importws.config.ImportWsJwtConfig;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;

/** import-ws verifies ws's Ed25519 access tokens with the public key only. */
class JwtUtilTest {
    private static final String EMAIL = "diver@test.ch";
    private static final KeyPair WS_KEYS = ed25519();

    private static KeyPair ed25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String publicPem(final KeyPair keys) {
        final var body =
                Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keys.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    private static Date inOneMinute() {
        return new Date(System.currentTimeMillis() + 60_000);
    }

    private static String wsShapedToken(final PrivateKey key) {
        return Jwts.builder()
                .header()
                .keyId("kid-of-ws-key")
                .and()
                .issuer("std-dive-logger")
                .subject(EMAIL)
                .claim("uid", 42L)
                .claim("email_verified", true)
                .issuedAt(new Date())
                .expiration(inOneMinute())
                .signWith(key, Jwts.SIG.EdDSA)
                .compact();
    }

    @Test
    void acceptsWsTokensWithThePublicKeyFromRealOrLiteralNewlinePem() {
        for (final var pem :
                new String[] {
                    publicPem(WS_KEYS), publicPem(WS_KEYS).strip().replace("\n", "\\n")
                }) {
            final var parser = JwtUtil.accessTokenParser(JwtUtil.parsePublicKey(pem));
            final var token = wsShapedToken(WS_KEYS.getPrivate());
            assertThat(JwtUtil.extractUsername(token, parser)).isEqualTo(EMAIL);
            assertThat(JwtUtil.isTokenValid(token, EMAIL, parser)).isTrue();
            assertThat(JwtUtil.isTokenValid(token, "other@test.ch", parser)).isFalse();
        }
    }

    @Test
    void rejectsOtherKeysHs256UnsignedAndIssuerlessTokens() {
        final var parser = JwtUtil.accessTokenParser(JwtUtil.parsePublicKey(publicPem(WS_KEYS)));
        final var tokens = new LinkedHashMap<String, String>();
        tokens.put("another Ed25519 key", wsShapedToken(ed25519().getPrivate()));
        tokens.put(
                "HS256 with the old shared secret",
                Jwts.builder()
                        .issuer("std-dive-logger")
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "integration-test-jwt-signing-secret-needs-to-be-long-enough"
                                                .getBytes()),
                                Jwts.SIG.HS256)
                        .compact());
        tokens.put(
                "HS256 keyed with the public key (alg confusion)",
                Jwts.builder()
                        .issuer("std-dive-logger")
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(
                                Keys.hmacShaKeyFor(WS_KEYS.getPublic().getEncoded()),
                                Jwts.SIG.HS256)
                        .compact());
        tokens.put(
                "unsigned",
                Jwts.builder()
                        .issuer("std-dive-logger")
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .compact());
        tokens.put(
                "no iss",
                Jwts.builder()
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(WS_KEYS.getPrivate(), Jwts.SIG.EdDSA)
                        .compact());

        tokens.forEach(
                (name, token) ->
                        assertThatThrownBy(() -> JwtUtil.extractUsername(token, parser))
                                .as(name)
                                .isInstanceOf(JwtException.class));
    }

    @Test
    void onlyAcceptsAnEd25519PublicKeyPem() {
        assertThatThrownBy(() -> JwtUtil.parsePublicKey("change-me"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PEM");
        final var privatePem =
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getEncoder().encodeToString(WS_KEYS.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----";
        assertThatThrownBy(() -> JwtUtil.parsePublicKey(privatePem))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingOrInvalidPublicKeyFailsStartupNamingThePropertyAndEnvVar() {
        for (final var value : new String[] {"", "change-me"}) {
            assertThatThrownBy(() -> new ImportWsJwtConfig(value).accessTokenParser())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ch.sthomas.stddivelogger.ws.jwt-public-key")
                    .hasMessageContaining("STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY");
        }
    }
}
