package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.UserEntity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;

import javax.crypto.SecretKey;

/**
 * A real end-to-end HTTP test for {@code MethodArgumentNotValidAdviceTrait} / {@code
 * ExceptionHandling}'s override of {@code handleMethodArgumentNotValid} - same rationale as {@code
 * HttpMessageNotReadableAdviceIntegrationTest}: a real HTTP request is the only kind of test that
 * would have caught a regression at the MVC exception-resolution layer (e.g. a plain
 * {@code @ExceptionHandler} trait method colliding with {@code ResponseEntityExceptionHandler}'s
 * own final dispatch, the same mistake {@code HttpMessageNotReadableAdviceTrait} had to work
 * around).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class MethodArgumentNotValidAdviceIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "method-argument-not-valid-it-jwt-signing-secret-needs-to-be-long-enough";
    private static final String TEST_USER_EMAIL = "method-argument-not-valid-it@test.ch";

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("ch.sthomas.stddivelogger.ws.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret",
                () -> "method-argument-not-valid-it-jwt-refresh-secret-needs-to-be-long-enough");
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

    @Autowired private RestTestClient restTestClient;
    @Autowired private UserRepository userRepository;

    private static String bearerToken() {
        final SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(TEST_USER_EMAIL)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    @Test
    void aCertificationAgencyBodyThatFailsBeanValidationReturnsItsRealFieldMessage() {
        userRepository.save(new UserEntity(TEST_USER_EMAIL, "hash", "MethodArgumentNotValid IT"));

        // "name" satisfies @NotBlank but is one character shorter than @Size(min = 2) allows -
        // every other field is left valid, so exactly one field violation is produced.
        final var body =
                """
                {
                  "name": "A",
                  "fullName": "Method Argument Not Valid IT Agency",
                  "websiteUrl": "https://example.com",
                  "description": null
                }
                """;

        restTestClient
                .post()
                .uri("/v1/certifications/agencies")
                .header("Authorization", "Bearer " + bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(
                        responseBody -> {
                            assertThat(responseBody)
                                    .contains(
                                            "\"detail\":\"name: size must be between 2 and"
                                                    + " 32\"");
                            assertThat(responseBody).doesNotContain("Invalid request content.");
                        });
    }
}
