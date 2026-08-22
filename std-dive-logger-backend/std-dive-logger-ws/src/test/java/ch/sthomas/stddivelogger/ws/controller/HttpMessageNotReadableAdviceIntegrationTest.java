package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DiveService;

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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

/**
 * A real end-to-end HTTP test for {@code HttpMessageNotReadableAdviceTrait} / {@code
 * ExceptionHandling}'s override of {@code handleHttpMessageNotReadable} - deliberately goes through
 * a real HTTP request rather than calling the controller/service layer directly, for two reasons:
 * (1) it's the only kind of test that would have caught the actual regression this class had during
 * development - a plain {@code @ExceptionHandler} trait method mapped to this same exception type
 * made the whole Spring context fail to start with "Ambiguous @ExceptionHandler method", something
 * a service-layer test can never exercise since it never boots the web MVC exception-resolution
 * machinery; and (2) the bug this fixes only manifests at the Jackson/HTTP-message-conversion
 * boundary, before a JSON body ever becomes a real Java object.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class HttpMessageNotReadableAdviceIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "http-message-not-readable-it-jwt-signing-secret-needs-to-be-long-enough";
    private static final String TEST_USER_EMAIL = "http-message-not-readable-it@test.ch";

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
                () -> "http-message-not-readable-it-jwt-refresh-secret-needs-to-be-long-enough");
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
    @Autowired private DiveSiteRepository diveSiteRepository;
    @Autowired private DiveService diveService;

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
    void aCylinderGasMixThatCannotSumToOneHundredPercentReturnsItsRealValidationMessage() {
        final var owner =
                userRepository
                        .save(new UserEntity(TEST_USER_EMAIL, "hash", "HttpMessageNotReadable IT"))
                        .toRecord();
        final var siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "HttpMessageNotReadable IT Site",
                                        new Location(47.0, 8.0).toPoint()))
                        .toRecord()
                        .id();
        final var diveId =
                diveService
                        .createEmptyDive(
                                owner,
                                new UploadDiveBody(
                                        1,
                                        "http-message-not-readable-it",
                                        siteId,
                                        18.0,
                                        Duration.ofMinutes(30),
                                        Instant.parse("2026-06-01T09:00:00Z")))
                        .id();

        // The exact shape the cylinder editor used to send before it was fixed to fill in n2 -
        // o2/he only, no n2/h2, which Jackson's canonical Gas constructor defaults to 0.0, failing
        // the "must sum to 100%" check for anything but pure O2.
        final var body =
                """
                {
                  "id": %d,
                  "number": 1,
                  "siteId": %d,
                  "configuration": {
                    "suit": { "id": 1 },
                    "base": "OTHER",
                    "weight": null,
                    "cylinders": [
                      {
                        "id": -1,
                        "size": { "unit": "LITER", "value": 12 },
                        "startBar": 200,
                        "endBar": 100,
                        "notes": "",
                        "gas": { "o2": 0.21, "he": 0 },
                        "role": "OC",
                        "usageStart": null,
                        "usageEnd": null
                      }
                    ]
                  }
                }
                """
                        .formatted(diveId, siteId);

        restTestClient
                .put()
                .uri("/v1/dives")
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
                                    .contains("\"detail\":\"Gas must consist of 100%\"");
                            assertThat(responseBody).doesNotContain("Failed to read request");
                        });
    }
}
