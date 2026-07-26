package ch.sthomas.stddivelogger.importws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftConfigResponse;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;

import java.util.Date;

/**
 * A real end-to-end test of the Divesoft import HTTP path against a throwaway Testcontainers
 * Postgres instance: confirms the new endpoints are actually reachable and covered by the
 * existing security filter chain (rather than silently falling outside its securityMatcher), and
 * that a posted dive JSON really gets persisted through the full stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Disabled("Requires a local Docker daemon reachable by Testcontainers - run manually")
class DivesoftImportIntegrationTest {
    private static final String TEST_JWT_SECRET =
            "integration-test-jwt-signing-secret-needs-to-be-long-enough";
    private static final String TEST_USER_EMAIL = "test@test.ch";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("ch.sthomas.stddivelogger.ws.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired private TestRestTemplate restTemplate;

    private static String bearerToken() {
        final SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(TEST_USER_EMAIL)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private static HttpHeaders authorizedJsonHeaders() {
        final var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void divesoftConfigEndpointRejectsUnauthenticatedRequests() {
        final var response =
                restTemplate.getForEntity("/v1/import/divesoft/config", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void divesoftImportEndpointRejectsUnauthenticatedRequests() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var response =
                restTemplate.postForEntity(
                        "/v1/import/divesoft",
                        new HttpEntity<>("{\"dives\":[]}", headers),
                        String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void divesoftConfigEndpointReturnsNonSecretAppConfigWhenAuthenticated() {
        final var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken());
        final var response =
                restTemplate.exchange(
                        "/v1/import/divesoft/config",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        DivesoftConfigResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().domain()).isEqualTo("wetnotes.eu.auth0.com");
        assertThat(response.getBody().realm()).isEqualTo("Username-Password-Authentication");
    }

    @Test
    void importDivesoftPersistsAMinimalSyntheticDiveEndToEnd() {
        final var requestBody =
                """
                {
                  "dives": [
                    {
                      "diveAndMixes": {
                        "dive": {
                          "id": "it-test-dive",
                          "deviceSerial": "IT-SERIAL",
                          "description": "",
                          "site": "Integration Test Lake",
                          "latitude": 47.0,
                          "longitude": 8.0,
                          "maxDepth": 10.0,
                          "averageDepth": 5.0,
                          "duration": "00:01:00",
                          "startDate": "Mon Jan 1 2024 00:00:00 GMT+0000 (Coordinated Universal Time)",
                          "mixes": [
                            { "id": 0, "o2": "21", "he": "0", "startPressure": 200, "endPressure": 180, "tankVolume": 12, "mixType": "air", "tankType": "oc" }
                          ],
                          "visibility": 5,
                          "cns": 2,
                          "diveData": { "avgDepth": 5.0, "startMode": "OC" },
                          "graphData": {
                            "depth": [ { "timestamp": 0, "value": 1.0 }, { "timestamp": 60, "value": 0.0 } ],
                            "temperature": [ { "timestamp": 0, "temperature": 20.0 }, { "timestamp": 60, "temperature": 20.0 } ],
                            "ceiling": [ { "timestamp": 0, "ceiling": 0.0 }, { "timestamp": 60, "ceiling": 0.0 } ],
                            "setpoint": [ { "timestamp": 0, "pressureInBar": 0.0 }, { "timestamp": 60, "pressureInBar": 0.0 } ],
                            "ppo2": [ { "timestamp": 0, "pressureInBar": 0.21 }, { "timestamp": 60, "pressureInBar": 0.21 } ],
                            "modes": [ { "timestamp": 0, "mode": "oc" } ],
                            "mixes": [ { "timestamp": 0, "mixO2": "21", "mixHe": "0", "mixType": "air" } ]
                          }
                        }
                      }
                    }
                  ],
                  "body": null
                }
                """;

        final var response =
                restTemplate.postForEntity(
                        "/v1/import/divesoft",
                        new HttpEntity<>(requestBody, authorizedJsonHeaders()),
                        UploadDiveResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().dives()).hasSize(1);
    }
}
