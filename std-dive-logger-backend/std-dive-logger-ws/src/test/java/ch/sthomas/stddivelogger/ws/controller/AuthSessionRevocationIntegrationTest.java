package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessions are revocable: a password change or "log out on all devices" kills every refresh token
 * and push subscription of the account, and an unusable refresh cookie is a clean 401 (never a 500)
 * - the frontend relies on that to tell "signed out" from "server unreachable".
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class AuthSessionRevocationIntegrationTest {

    private static final String REFRESH_SECRET =
            "auth-session-it-jwt-refresh-secret-that-is-comfortably-over-48-chars";
    private static final String PASSWORD = "Dive!Log7Xq";
    private static final String NEW_PASSWORD = "Reef#Mask9Zp";

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-secret",
                () -> "auth-session-it-jwt-signing-secret-needs-to-be-long-enough");
        registry.add("ch.sthomas.stddivelogger.ws.jwt-refresh-secret", () -> REFRESH_SECRET);
        registry.add("ch.sthomas.stddivelogger.push.vapid.public-key", () -> "BTestPublicKey");
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
    @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private record Session(String accessToken, String refreshCookie) {}

    private String seedUser() {
        final var email = "auth-session-it-" + UUID.randomUUID() + "@test.ch";
        userRepository.save(
                new UserEntity(
                        email,
                        passwordEncoder.encode(PASSWORD),
                        "Session " + UUID.randomUUID(),
                        true));
        return email;
    }

    private Session login(final String email, final String password) {
        final var result =
                client.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(AuthResponse.class)
                        .returnResult();
        return new Session(
                Objects.requireNonNull(result.getResponseBody()).accessToken(),
                refreshCookieOf(result.getResponseHeaders()));
    }

    private static String refreshCookieOf(final HttpHeaders headers) {
        final var setCookie = Objects.requireNonNull(headers.getFirst(HttpHeaders.SET_COOKIE));
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    private RestTestClient.ResponseSpec refresh(final String cookie) {
        return client.post().uri("/api/auth/refresh").cookie("refresh_token", cookie).exchange();
    }

    private void subscribePush(final Session session, final String endpoint) {
        client.post()
                .uri("/v1/push/subscriptions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("endpoint", endpoint, "keys", Map.of("p256dh", "p", "auth", "a")))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.logbookSync")
                .isEqualTo(true);
    }

    private long pushSubscriptionsOf(final String email) {
        final var id = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        return pushSubscriptionRepository.findByUserId(id).size();
    }

    @Test
    void unusableRefreshCookiesAre401NotServerErrors() {
        refresh("not-a-jwt").expectStatus().isUnauthorized();

        final var expired =
                Jwts.builder()
                        .subject("nobody@test.ch")
                        .id(UUID.randomUUID().toString())
                        .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                        .expiration(new Date(System.currentTimeMillis() - 60_000))
                        .signWith(Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes()))
                        .compact();
        refresh(expired).expectStatus().isUnauthorized();

        // Correctly signed but never issued (no row) - e.g. revoked.
        final var unknown =
                Jwts.builder()
                        .subject("nobody@test.ch")
                        .id(UUID.randomUUID().toString())
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000))
                        .signWith(Keys.hmacShaKeyFor(REFRESH_SECRET.getBytes()))
                        .compact();
        refresh(unknown).expectStatus().isUnauthorized();

        client.post().uri("/api/auth/refresh").exchange().expectStatus().isBadRequest();
    }

    @Test
    void logoutWithAnExpiredCookieStillClearsIt() {
        client.post()
                .uri("/api/auth/logout")
                .cookie("refresh_token", "not-a-jwt")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .value(HttpHeaders.SET_COOKIE, v -> assertThat(v).contains("Max-Age=0"));
    }

    @Test
    void passwordChangeSignsOutEveryOtherDeviceAndKeepsThisOne() {
        final var email = seedUser();
        final var phone = login(email, PASSWORD);
        final var laptop = login(email, PASSWORD);
        refresh(phone.refreshCookie()).expectStatus().isOk();
        subscribePush(phone, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID());
        assertThat(pushSubscriptionsOf(email)).isEqualTo(1);

        final var changed =
                client.post()
                        .uri("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(AuthResponse.class)
                        .returnResult();
        final var laptopAfter = refreshCookieOf(changed.getResponseHeaders());

        refresh(phone.refreshCookie()).expectStatus().isUnauthorized();
        refresh(laptop.refreshCookie()).expectStatus().isUnauthorized();
        refresh(laptopAfter).expectStatus().isOk();
        assertThat(pushSubscriptionsOf(email)).isZero();
        login(email, NEW_PASSWORD);
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus()
                .is4xxClientError();
    }

    @Test
    void passwordChangeRejectsAWrongCurrentPasswordAndAWeakNewOne() {
        final var email = seedUser();
        final var session = login(email, PASSWORD);

        for (final var body :
                List.of(
                        Map.of("currentPassword", "Wrong!Pass9", "newPassword", NEW_PASSWORD),
                        Map.of("currentPassword", PASSWORD, "newPassword", "short"),
                        Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))) {
            client.post()
                    .uri("/api/auth/password")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        refresh(session.refreshCookie()).expectStatus().isOk();
    }

    @Test
    void passwordChangeAndLogoutAllNeedAnAccessToken() {
        client.post()
                .uri("/api/auth/password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post().uri("/api/auth/logout-all").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void logoutEverywhereRevokesEverySessionAndSubscription() {
        final var email = seedUser();
        final var phone = login(email, PASSWORD);
        final var laptop = login(email, PASSWORD);
        subscribePush(laptop, "https://web.push.apple.com/" + UUID.randomUUID());

        client.post()
                .uri("/api/auth/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + phone.accessToken())
                .exchange()
                .expectStatus()
                .isNoContent();

        refresh(phone.refreshCookie()).expectStatus().isUnauthorized();
        refresh(laptop.refreshCookie()).expectStatus().isUnauthorized();
        assertThat(pushSubscriptionsOf(email)).isZero();
    }

    @Test
    void deletingTheAccountRevokesItsSessions() {
        final var email = seedUser();
        final var session = login(email, PASSWORD);

        client.post()
                .uri("/api/auth/deregister")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .exchange()
                .expectStatus()
                .isOk();

        refresh(session.refreshCookie()).expectStatus().isUnauthorized();
    }

    @Test
    void pushIsEnabledWithOnlyThePublicKeyConfigured() {
        final var session = login(seedUser(), PASSWORD);
        client.get()
                .uri("/v1/push/public-key")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.enabled")
                .isEqualTo(true);
    }
}
