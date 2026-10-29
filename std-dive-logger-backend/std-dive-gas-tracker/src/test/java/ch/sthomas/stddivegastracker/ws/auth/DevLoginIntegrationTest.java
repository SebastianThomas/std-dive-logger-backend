package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.support.AbstractIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * The local-only token endpoint (profile dev-login + flag); a second, separate context that holds
 * the configured test key AND the ephemeral dev key.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev-login",
            "spring.docker.compose.enabled=false",
            "ch.sthomas.stddivegastracker.auth.require-email-verified-claim=true",
            "ch.sthomas.stddivegastracker.auth.dev-login-enabled=true",
        })
final class DevLoginIntegrationTest extends AbstractIntegrationTest {

    @Test
    void mintsAVerifiedTokenThatTheApiAccepts() {
        final var token = devLogin("Grace@Example.test");

        final var me = me("Bearer " + token);

        assertThat(me.email()).isEqualTo("grace@example.test");
    }

    @Test
    void theTokenIsEdDsaSignedWithAKidAndCarriesTheDevClaims() {
        final var token = devLogin("claims@example.test");

        final var parts = token.split("\\.", -1);
        final var header = decode(parts[0]);
        final var payload = decode(parts[1]);
        assertThat(header)
                .contains("\"alg\":\"EdDSA\"")
                .containsPattern("\"kid\":\"[A-Za-z0-9_-]{16}\"");
        assertThat(header).doesNotContain(TestJwts.KEY_ID);
        assertThat(payload)
                .contains("\"iss\":\"std-dive-logger\"")
                .contains("\"sub\":\"claims@example.test\"")
                .contains("\"email_verified\":true")
                .containsPattern("\"uid\":-[0-9]+");
    }

    @Test
    void theUserIdIsNegativeStablePerEmailAndDifferentBetweenEmails() {
        final var first = me("Bearer " + devLogin("stable@example.test")).userId();
        final var again = me("Bearer " + devLogin("STABLE@Example.test")).userId();
        final var other = me("Bearer " + devLogin("different@example.test")).userId();

        assertThat(first).isNegative().isEqualTo(again).isNotEqualTo(other);
        assertThat(other).isNegative();
    }

    @Test
    void theUserIdFollowsTheDocumentedFormula() {
        // -(1 + floorMod(first 8 bytes of SHA-256(email) as a big-endian long, 1e12)). For this
        // email that long is negative (df9a9079744a8548 = -2334394605521697464), so a plain %
        // instead of floorMod would give a different id. Cross-checked in Python.
        assertThat(DevLoginController.devUserId("formula@example.test"))
                .isEqualTo(-394_478_302_537L);
    }

    @Test
    void devLoginDoesNotMakeTheConfiguredKeyLessStrict() {
        // The configured (dive-logger) key still verifies, but may not use the dev id range.
        client.get()
                .uri("/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(-1L, "negative@example.test"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.get()
                .uri("/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(5_001L, "positive@example.test"))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void rejectsAnInvalidEmail() {
        client.post()
                .uri("/api/dev/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "nope"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private String devLogin(final String email) {
        return Objects.requireNonNull(
                        client.post()
                                .uri("/api/dev/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of("email", email))
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(DevLoginController.DevLoginResponse.class)
                                .returnResult()
                                .getResponseBody())
                .accessToken();
    }

    private MeResponse me(final String authorization) {
        return Objects.requireNonNull(
                client.get()
                        .uri("/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(MeResponse.class)
                        .returnResult()
                        .getResponseBody());
    }

    private static String decode(final String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }
}
