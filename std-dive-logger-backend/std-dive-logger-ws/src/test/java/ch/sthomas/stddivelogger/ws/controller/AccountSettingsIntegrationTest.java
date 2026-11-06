package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.AccountSettings;
import ch.sthomas.stddivelogger.ws.auth.TestJwtKeys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Account settings: username change (unique, case-insensitive), email change confirmed through a
 * link mailed to the new address (signs every device out), and resending the verification link.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class AccountSettingsIntegrationTest {

    private static final String PASSWORD = "Dive!Log7Xq";
    private static final Pattern CHANGE_TOKEN =
            Pattern.compile("/user/email/change\\?token=(\\S+)");

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
                () -> "account-settings-it-jwt-refresh-secret-comfortably-over-48-chars");
        registry.add("ch.sthomas.stddivelogger.push.vapid.public-key", () -> "BTestPublicKey");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    @Autowired private RestTestClient client;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record Session(String accessToken, String refreshCookie) {}

    private String seedUser(final String name, final boolean verified) {
        final var email = "account-it-" + UUID.randomUUID() + "@test.ch";
        userRepository.save(
                new UserEntity(email, passwordEncoder.encode(PASSWORD), name, verified));
        return email;
    }

    private static String uniqueName() {
        return "Diver " + UUID.randomUUID();
    }

    private static String newAddress() {
        return "account-it-new-" + UUID.randomUUID() + "@test.ch";
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

    private AccountSettings settings(final Session session) {
        return Objects.requireNonNull(
                client.get()
                        .uri("/v1/users/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(AccountSettings.class)
                        .returnResult()
                        .getResponseBody());
    }

    private RestTestClient.ResponseSpec changeName(final Session session, final String name) {
        return client.put()
                .uri("/v1/users/me/name")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .exchange();
    }

    private RestTestClient.ResponseSpec requestEmailChange(
            final Session session, final String password, final String newEmail) {
        return client.post()
                .uri("/api/auth/email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", password, "newEmail", newEmail))
                .exchange();
    }

    private RestTestClient.ResponseSpec confirm(final String token) {
        return client.post()
                .uri("/api/auth/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("token", token))
                .exchange();
    }

    /** The token from the newest confirmation mail queued for {@code address}. */
    private String mailedChangeToken(final String address) {
        final var content =
                jdbcTemplate.queryForObject(
                        "SELECT content FROM t_email WHERE send_to = ? ORDER BY pk_email_id DESC"
                                + " LIMIT 1",
                        String.class,
                        address);
        final var matcher = CHANGE_TOKEN.matcher(Objects.requireNonNull(content));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void settingsShowTheAccountsOwnDetailsAndNeedALogin() {
        final var name = uniqueName();
        final var email = seedUser(name, true);
        final var account = settings(login(email));
        assertThat(account.name()).isEqualTo(name);
        assertThat(account.email()).isEqualTo(email);
        assertThat(account.emailVerified()).isTrue();
        assertThat(account.pendingEmail()).isNull();

        client.get().uri("/v1/users/me/account").exchange().expectStatus().isUnauthorized();
        client.put()
                .uri("/v1/users/me/name")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "x"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/auth/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("currentPassword", PASSWORD, "newEmail", newAddress()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/auth/verify-email/resend")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void usernameChangeIsTrimmedAndUniqueIgnoringCase() {
        final var taken = uniqueName();
        seedUser(taken, true);
        final var session = login(seedUser(uniqueName(), true));

        final var wanted = uniqueName();
        changeName(session, "  " + wanted + "  ")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo(wanted);
        assertThat(settings(session).name()).isEqualTo(wanted);

        // A case-only change of one's own name is not a clash.
        changeName(session, wanted.toUpperCase()).expectStatus().isOk();

        changeName(session, taken.toLowerCase()).expectStatus().isBadRequest();
        changeName(session, "   ").expectStatus().isBadRequest();
        changeName(session, "x".repeat(65)).expectStatus().isBadRequest();
        assertThat(settings(session).name()).isEqualTo(wanted.toUpperCase());
    }

    @Test
    void emailChangeWaitsForTheLinkThenSignsEveryDeviceOut() {
        final var oldEmail = seedUser(uniqueName(), true);
        final var phone = login(oldEmail);
        final var laptop = login(oldEmail);
        final var newEmail = newAddress();

        requestEmailChange(laptop, "Wrong!Pass9", newEmail).expectStatus().isBadRequest();
        requestEmailChange(laptop, PASSWORD, "not-an-email").expectStatus().isBadRequest();
        requestEmailChange(laptop, PASSWORD, oldEmail.toUpperCase()).expectStatus().isBadRequest();
        requestEmailChange(laptop, PASSWORD, seedUser(uniqueName(), true))
                .expectStatus()
                .isBadRequest();

        requestEmailChange(laptop, PASSWORD, newEmail)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.pendingEmail")
                .isEqualTo(newEmail)
                .jsonPath("$.email")
                .isEqualTo(oldEmail);
        final var firstToken = mailedChangeToken(newEmail);

        // Asking again replaces the first link.
        requestEmailChange(laptop, PASSWORD, newEmail).expectStatus().isOk();
        final var token = mailedChangeToken(newEmail);
        assertThat(token).isNotEqualTo(firstToken);
        confirm(firstToken).expectStatus().isBadRequest();

        // Nothing changes before the link is opened.
        login(oldEmail);

        confirm(token)
                .expectStatus()
                .isNoContent()
                .expectHeader()
                .value(HttpHeaders.SET_COOKIE, v -> assertThat(v).contains("Max-Age=0"));
        confirm(token).expectStatus().isBadRequest();

        client.post()
                .uri("/api/auth/refresh")
                .cookie("refresh_token", phone.refreshCookie())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.get()
                .uri("/v1/users/me/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.accessToken())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", oldEmail, "password", PASSWORD))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        final var account = settings(login(newEmail));
        assertThat(account.email()).isEqualTo(newEmail);
        assertThat(account.emailVerified()).isTrue();
        assertThat(account.pendingEmail()).isNull();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM t_email WHERE send_to = ?",
                                Long.class,
                                oldEmail))
                .as("a notice goes to the address the account left")
                .isEqualTo(1L);
    }

    @Test
    void aPendingEmailChangeCanBeCancelled() {
        final var session = login(seedUser(uniqueName(), true));
        final var newEmail = newAddress();
        requestEmailChange(session, PASSWORD, newEmail).expectStatus().isOk();
        final var token = mailedChangeToken(newEmail);

        client.delete()
                .uri("/api/auth/email")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.pendingEmail")
                .doesNotExist();
        confirm(token).expectStatus().isBadRequest();
        confirm("garbage").expectStatus().isBadRequest();
    }

    @Test
    void verificationLinkCanBeResentOnlyWhileUnverified() {
        final var unverified = seedUser(uniqueName(), false);
        final var session = login(unverified);
        final var before =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM t_email WHERE receiver = ?", Long.class, unverified);
        client.post()
                .uri("/api/auth/verify-email/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
                .exchange()
                .expectStatus()
                .isOk();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM t_email WHERE receiver = ?",
                                Long.class,
                                unverified))
                .isEqualTo(Objects.requireNonNull(before) + 1);

        final var verified = login(seedUser(uniqueName(), true));
        client.post()
                .uri("/api/auth/verify-email/resend")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + verified.accessToken())
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
