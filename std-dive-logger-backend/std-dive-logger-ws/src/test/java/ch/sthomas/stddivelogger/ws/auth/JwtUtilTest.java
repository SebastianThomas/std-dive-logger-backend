package ch.sthomas.stddivelogger.ws.auth;

import static ch.sthomas.stddivelogger.ws.auth.JwtUtil.TokenType.ACCESS_TOKEN;
import static ch.sthomas.stddivelogger.ws.auth.JwtUtil.TokenType.REFRESH_TOKEN;
import static ch.sthomas.stddivelogger.ws.auth.TestJwtKeys.KEY_PAIR;
import static ch.sthomas.stddivelogger.ws.auth.TestJwtKeys.PRIVATE_KEY_PEM;
import static ch.sthomas.stddivelogger.ws.auth.TestJwtKeys.PUBLIC_KEY_PEM;
import static ch.sthomas.stddivelogger.ws.auth.TestJwtKeys.pem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.model.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/** Access-token format (Ed25519, kid, iss/sub/uid/email_verified) and fail-fast key config. */
class JwtUtilTest {
    private static final String REFRESH_SECRET =
            "jwt-util-test-refresh-secret-that-is-comfortably-over-48-chars";
    private static final String EMAIL = "diver@test.ch";

    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JwtUtil jwtUtil = jwtUtil(PRIVATE_KEY_PEM, PUBLIC_KEY_PEM);

    private JwtUtil jwtUtil(final String signingKeyPem, final String publicKeyPem) {
        return new JwtUtil(signingKeyPem, publicKeyPem, REFRESH_SECRET, refreshTokens);
    }

    private static User user(final long id, final boolean emailVerified) {
        return new User(
                id,
                EMAIL,
                "hash",
                "Diver",
                emailVerified,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null);
    }

    /** What import-ws and the gas tracker do: plain jjwt with only the public key. */
    private static Jws<Claims> verifyWithPublicKey(final String token) {
        return Jwts.parser().verifyWith(KEY_PAIR.getPublic()).build().parseSignedClaims(token);
    }

    private static Date inOneMinute() {
        return new Date(System.currentTimeMillis() + 60_000);
    }

    @Test
    void accessTokensCarryKidIssuerSubjectUidAndEmailVerified() {
        for (final var verified : List.of(true, false)) {
            final var token = jwtUtil.generateAccessToken(user(42, verified));

            final var jws = verifyWithPublicKey(token);
            assertThat(jws.getHeader().getAlgorithm()).isEqualTo("EdDSA");
            assertThat(jws.getHeader().getKeyId())
                    .isEqualTo(AccessTokenKeys.keyId(KEY_PAIR.getPublic()))
                    .hasSize(16);
            final var claims = jws.getPayload();
            assertThat(claims.getIssuer()).isEqualTo("std-dive-logger");
            assertThat(claims.getSubject()).isEqualTo(EMAIL);
            assertThat(claims.get("uid", Long.class)).isEqualTo(42L);
            assertThat(claims.get("email_verified", Boolean.class)).isEqualTo(verified);
            assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                    .isEqualTo(JwtUtil.ACCESS_TOKEN_LIFETIME.toMillis());

            assertThat(jwtUtil.extractUsername(token, ACCESS_TOKEN)).isEqualTo(EMAIL);
            assertThat(jwtUtil.isTokenValid(token, EMAIL, ACCESS_TOKEN)).isTrue();
        }
    }

    /** Vector from tools/generate-jwt-keys.sh (openssl), so the gas tracker can match it. */
    @Test
    void keyIdIsTheBase64UrlSha256OfThePublicKeyDerCutTo16Chars() {
        final var publicKey =
                AccessTokenKeys.parsePublicKey(
                        "-----BEGIN PUBLIC KEY-----\\n"
                                + "MCowBQYDK2VwAyEAGRBvwId/pzC1EIy/6eGX6UuI1OcZROlB62PePNtnnDQ=\\n"
                                + "-----END PUBLIC KEY-----");
        assertThat(AccessTokenKeys.keyId(publicKey)).isEqualTo("j9pdJi3GdGiQWqc6");
    }

    @Test
    void foreignHs256UnsignedIssuerlessAndRefreshTokensAreNoAccessTokens() {
        final var otherKey = TestJwtKeys.generate();
        final var tokens = new LinkedHashMap<String, String>();
        tokens.put(
                "another Ed25519 key",
                Jwts.builder()
                        .issuer(JwtUtil.ISSUER)
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(otherKey.getPrivate(), Jwts.SIG.EdDSA)
                        .compact());
        tokens.put(
                "HS256 with an old-style access-token secret",
                Jwts.builder()
                        .issuer(JwtUtil.ISSUER)
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "dev-verify-jwt-secret-0123456789-0123456789".getBytes()),
                                Jwts.SIG.HS256)
                        .compact());
        tokens.put(
                "HS256 keyed with the public key (alg confusion)",
                Jwts.builder()
                        .issuer(JwtUtil.ISSUER)
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(
                                Keys.hmacShaKeyFor(KEY_PAIR.getPublic().getEncoded()),
                                Jwts.SIG.HS256)
                        .compact());
        tokens.put(
                "unsigned (alg none)",
                Jwts.builder()
                        .issuer(JwtUtil.ISSUER)
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .compact());
        tokens.put(
                "no iss",
                Jwts.builder()
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.EdDSA)
                        .compact());
        tokens.put(
                "another iss",
                Jwts.builder()
                        .issuer("someone-else")
                        .subject(EMAIL)
                        .expiration(inOneMinute())
                        .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.EdDSA)
                        .compact());
        tokens.put("a refresh token", jwtUtil.generateRefreshToken(42, EMAIL));

        tokens.forEach(
                (name, token) ->
                        assertThatThrownBy(() -> jwtUtil.extractUsername(token, ACCESS_TOKEN))
                                .as(name)
                                .isInstanceOf(JwtException.class));
    }

    @Test
    void refreshTokensStayHs384WithTheRefreshSecret() {
        final var token = jwtUtil.generateRefreshToken(42, EMAIL);

        final var jws =
                Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes()))
                        .build()
                        .parseSignedClaims(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS384");
        assertThat(jwtUtil.extractUsername(token, REFRESH_TOKEN)).isEqualTo(EMAIL);
        assertThatThrownBy(
                        () ->
                                jwtUtil.extractUsername(
                                        jwtUtil.generateAccessToken(user(42, true)), REFRESH_TOKEN))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void keysAlsoParseFromOneLineWithLiteralBackslashN() {
        final var util =
                jwtUtil(
                        PRIVATE_KEY_PEM.strip().replace("\n", "\\n"),
                        PUBLIC_KEY_PEM.strip().replace("\n", "\\n"));
        final var claims = verifyWithPublicKey(util.generateAccessToken(user(7, true)));
        assertThat(claims.getPayload().get("uid", Long.class)).isEqualTo(7L);
    }

    @Test
    void missingInvalidOrMismatchedKeysFailFastNamingThePropertyAndEnvVar() throws Exception {
        assertThatThrownBy(() -> jwtUtil("", PUBLIC_KEY_PEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ch.sthomas.stddivelogger.ws.jwt-signing-key")
                .hasMessageContaining("STD_DIVE_LOGGER_WS_JWT_SIGNING_KEY")
                .hasMessageContaining("is not set");
        assertThatThrownBy(() -> jwtUtil(PRIVATE_KEY_PEM, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ch.sthomas.stddivelogger.ws.jwt-public-key")
                .hasMessageContaining("STD_DIVE_LOGGER_WS_JWT_PUBLIC_KEY")
                .hasMessageContaining("is not set");
        assertThatThrownBy(() -> jwtUtil("change-me", PUBLIC_KEY_PEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-signing-key")
                .hasMessageContaining("PEM");
        // Swapped halves.
        assertThatThrownBy(() -> jwtUtil(PUBLIC_KEY_PEM, PRIVATE_KEY_PEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-signing-key");

        final var otherPublic = pem("PUBLIC KEY", TestJwtKeys.generate().getPublic().getEncoded());
        assertThatThrownBy(() -> jwtUtil(PRIVATE_KEY_PEM, otherPublic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not the public key of");

        final var rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        assertThatThrownBy(
                        () ->
                                jwtUtil(
                                        pem("PRIVATE KEY", rsa.getPrivate().getEncoded()),
                                        PUBLIC_KEY_PEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an Ed25519");
        final var ed448 = KeyPairGenerator.getInstance("Ed448").generateKeyPair();
        assertThatThrownBy(
                        () ->
                                jwtUtil(
                                        PRIVATE_KEY_PEM,
                                        pem("PUBLIC KEY", ed448.getPublic().getEncoded())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt-public-key");
    }
}
