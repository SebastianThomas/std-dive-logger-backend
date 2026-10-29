package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.ws.auth.AccessTokenKeys;
import ch.sthomas.stddivelogger.ws.auth.JwtUtil;
import ch.sthomas.stddivelogger.ws.auth.TestJwtKeys;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Access tokens from login and refresh are Ed25519-signed with kid/iss/uid/email_verified and work
 * against the API; anything else in the bearer header (another key, the old HS256 format, no iss, a
 * refresh token) is a 401, never a 500.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class AccessTokenIntegrationTest {

    private static final String PASSWORD = "Dive!Log7Xq";

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        TestJwtKeys.register(registry);
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret",
                () -> "access-token-it-jwt-refresh-secret-that-is-comfortably-over-48-chars");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.bucket", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.account-id", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.access-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.secret-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    @Autowired private RestTestClient client;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private record Session(String accessToken, String refreshCookie) {}

    private User seedUser(final boolean emailVerified) {
        return userRepository
                .save(
                        new UserEntity(
                                "access-token-it-" + UUID.randomUUID() + "@test.ch",
                                passwordEncoder.encode(PASSWORD),
                                "Token " + UUID.randomUUID(),
                                emailVerified))
                .toRecord();
    }

    private Session login(final String email) {
        final var result =
                client.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(AuthResponse.class)
                        .returnResult();
        final var setCookie =
                Objects.requireNonNull(
                        result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE));
        return new Session(
                Objects.requireNonNull(result.getResponseBody()).accessToken(),
                setCookie.substring("refresh_token=".length(), setCookie.indexOf(';')));
    }

    private String refresh(final String refreshCookie) {
        return Objects.requireNonNull(
                client.post()
                        .uri("/api/auth/refresh")
                        .cookie("refresh_token", refreshCookie)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody());
    }

    private RestTestClient.ResponseSpec home(final String bearer) {
        return client.get()
                .uri("/v1/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                .exchange();
    }

    /** Verifies the way import-ws and the gas tracker do: the public key alone. */
    private static Claims verifiedClaims(final String token) {
        final var jws =
                Jwts.parser()
                        .verifyWith(TestJwtKeys.KEY_PAIR.getPublic())
                        .build()
                        .parseSignedClaims(token);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("EdDSA");
        assertThat(jws.getHeader().getKeyId())
                .isEqualTo(AccessTokenKeys.keyId(TestJwtKeys.KEY_PAIR.getPublic()));
        return jws.getPayload();
    }

    private static String signed(
            final String email, @Nullable final String issuer, final PrivateKey key) {
        final var builder =
                Jwts.builder()
                        .subject(email)
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000));
        if (issuer != null) {
            builder.issuer(issuer);
        }
        return builder.signWith(key, Jwts.SIG.EdDSA).compact();
    }

    @Test
    void loginAndRefreshIssueEd25519TokensThatCarryUidAndEmailVerifiedAndWork() {
        for (final var verified : List.of(true, false)) {
            final var user = seedUser(verified);
            final var session = login(user.email());

            for (final var token :
                    List.of(session.accessToken(), refresh(session.refreshCookie()))) {
                final var claims = verifiedClaims(token);
                assertThat(claims.getIssuer()).isEqualTo("std-dive-logger");
                assertThat(claims.getSubject()).isEqualTo(user.email());
                assertThat(claims.get("uid", Long.class)).isEqualTo(user.id());
                assertThat(claims.get("email_verified", Boolean.class)).isEqualTo(verified);
                assertThat(claims.getExpiration()).isAfter(new Date());

                // check-verified is off in the default (local) profile set.
                home(token).expectStatus().isOk().expectBody().jsonPath("$.userName").exists();
            }
        }
    }

    @Test
    void foreignOldFormatIssuerlessAndRefreshTokensAre401() {
        final var email = seedUser(true).email();
        final var session = login(email);
        home(session.accessToken()).expectStatus().isOk();

        final var ownKey = TestJwtKeys.KEY_PAIR.getPrivate();
        final var oldFormatHs256 =
                Jwts.builder()
                        .subject(email)
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000))
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "dev-verify-jwt-secret-0123456789-0123456789".getBytes()),
                                Jwts.SIG.HS256)
                        .compact();
        final var expired =
                Jwts.builder()
                        .issuer(JwtUtil.ISSUER)
                        .subject(email)
                        .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                        .expiration(new Date(System.currentTimeMillis() - 60_000))
                        .signWith(ownKey, Jwts.SIG.EdDSA)
                        .compact();

        for (final var token :
                List.of(
                        signed(email, JwtUtil.ISSUER, TestJwtKeys.generate().getPrivate()),
                        oldFormatHs256,
                        signed(email, null, ownKey),
                        signed(email, "someone-else", ownKey),
                        expired,
                        session.refreshCookie(),
                        "not-a-jwt")) {
            home(token).expectStatus().isUnauthorized();
        }
    }
}
