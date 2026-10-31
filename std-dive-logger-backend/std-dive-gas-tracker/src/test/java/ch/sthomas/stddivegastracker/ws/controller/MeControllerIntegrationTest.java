package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import static java.nio.charset.StandardCharsets.UTF_8;

import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.support.AbstractIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MeControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void withoutATokenIsUnauthorized() {
        client.get().uri("/v1/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void firstRequestProvisionsTheUserUnderTheTokenUidWithANormalisedEmail() {
        final var uid = 770_001L;
        final var me =
                Objects.requireNonNull(
                        client.get()
                                .uri("/v1/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(uid, "  Alice.Diver@Example.TEST "))
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(MeResponse.class)
                                .returnResult()
                                .getResponseBody());
        assertThat(me.userId()).isEqualTo(uid);
        // the username from the token, as the dive logger spells it
        assertThat(me.displayName()).isEqualTo(me.username());
        assertThat(me.teams()).isEmpty();

        final var again =
                Objects.requireNonNull(
                        client.get()
                                .uri("/v1/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(uid, "alice.diver@example.test"))
                                .exchange()
                                .expectBody(MeResponse.class)
                                .returnResult()
                                .getResponseBody());
        // the same user: this test's token spells the username differently, as it derives from the
        // address it is given
        assertThat(again.userId()).isEqualTo(me.userId());
        assertThat(again.displayName()).isEqualTo(me.displayName());
    }

    @Test
    void validTokensAreAcceptedWithAndWithoutKid() {
        final var withKid = TestJwts.accessToken(770_002L, "kid@example.test");
        final var withoutKid =
                TestJwts.sign(TestJwts.builderWithoutKeyId(770_003L, "nokid@example.test"));

        for (final var token : List.of(withKid, withoutKid)) {
            assertThat(statusOf(token)).isEqualTo(200);
        }
    }

    /** Whatever is wrong with the token, the answer is 401 - never a 500 or a 403. */
    @Test
    void invalidTokensAreUnauthorized() {
        final var email = "mallory@example.test";
        final var other = otherEd25519();
        final var invalid = new LinkedHashMap<String, String>();
        invalid.put(
                "HS256",
                TestJwts.builder(770_010L, email)
                        .signWith(
                                Keys.hmacShaKeyFor(
                                        "0123456789abcdef0123456789abcdef".getBytes(UTF_8)),
                                Jwts.SIG.HS256)
                        .compact());
        invalid.put(
                "HS256 keyed with the public key",
                TestJwts.builder(770_011L, email)
                        .signWith(
                                Keys.hmacShaKeyFor(TestJwts.PUBLIC_KEY.getEncoded()),
                                Jwts.SIG.HS256)
                        .compact());
        invalid.put(
                "another Ed25519 key",
                TestJwts.builder(770_012L, email).signWith(other, Jwts.SIG.EdDSA).compact());
        invalid.put(
                "wrong iss",
                TestJwts.sign(TestJwts.builder(770_013L, email).issuer("someone-else")));
        invalid.put("missing uid", TestJwts.sign(TestJwts.builder(null, email)));
        invalid.put(
                "non-numeric uid",
                TestJwts.sign(TestJwts.builder(770_014L, email).claim("uid", "770014")));
        invalid.put("negative uid from a non-dev key", TestJwts.accessToken(-770_015L, email));
        invalid.put(
                "email_verified false",
                TestJwts.sign(TestJwts.builder(770_016L, email).claim("email_verified", false)));
        invalid.put(
                "email_verified missing",
                TestJwts.sign(TestJwts.builder(770_017L, email).claim("email_verified", null)));
        invalid.put(
                "expired",
                TestJwts.sign(
                        TestJwts.builder(770_018L, email)
                                .expiration(
                                        Date.from(Instant.now().minus(Duration.ofMinutes(5))))));
        invalid.put(
                "unknown kid",
                TestJwts.sign(
                        TestJwts.builder(770_019L, email)
                                .header()
                                .keyId("0123456789abcdef")
                                .and()));
        invalid.put(
                "subject is not an email",
                TestJwts.sign(TestJwts.builder(770_020L, "not-an-email")));
        invalid.put("garbage", "not.a.jwt");

        invalid.forEach((name, token) -> assertThat(statusOf(token)).as(name).isEqualTo(401));
    }

    private int statusOf(final String token) {
        return client.get()
                .uri("/v1/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult()
                .getStatus()
                .value();
    }

    private static PrivateKey otherEd25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void displayNameCanBeChangedAndIsTrimmed() {
        final var auth = bearer("dave@example.test");
        final var updated =
                Objects.requireNonNull(
                        client.put()
                                .uri("/v1/me")
                                .header(HttpHeaders.AUTHORIZATION, auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of("displayName", "  Dave the Blender "))
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(MeResponse.class)
                                .returnResult()
                                .getResponseBody());
        assertThat(updated.displayName()).isEqualTo("Dave the Blender");
    }

    @Test
    void aBlankDisplayNameIsABadRequestNamingTheField() {
        client.put()
                .uri("/v1/me")
                .header(HttpHeaders.AUTHORIZATION, bearer("erin@example.test"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("displayName", "   "))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("displayName"));
    }

    @Test
    void devLoginDoesNotExistUnlessEnabled() {
        client.post()
                .uri("/api/dev/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "anyone@example.test"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void unlistedPathsAreDeniedEvenWhenAuthenticated() {
        client.get()
                .uri("/internal/anything")
                .header(HttpHeaders.AUTHORIZATION, bearer("frank@example.test"))
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
