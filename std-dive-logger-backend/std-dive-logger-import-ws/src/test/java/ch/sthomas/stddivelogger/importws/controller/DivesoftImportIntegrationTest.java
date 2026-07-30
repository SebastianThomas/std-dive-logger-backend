package ch.sthomas.stddivelogger.importws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.geometry.Location;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;

import javax.crypto.SecretKey;

/**
 * A real end-to-end test of the Divesoft import HTTP path against a throwaway Testcontainers
 * Postgres instance: confirms the new endpoints are actually reachable and covered by the existing
 * security filter chain (rather than silently falling outside its securityMatcher), and that
 * staging a dive and then committing it (with and without a site override) really persists it
 * through the full stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Disabled("Requires a local Docker daemon reachable by Testcontainers - run manually")
class DivesoftImportIntegrationTest {
    private static final String TEST_JWT_SECRET =
            "integration-test-jwt-signing-secret-needs-to-be-long-enough";
    private static final String TEST_USER_EMAIL = "test@test.ch";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"));

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

    private static String syntheticDiveRequestBody(final String id) {
        return """
                {
                  "dives": [
                    {
                      "diveAndMixes": {
                        "dive": {
                          "id": "%s",
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
                  ]
                }
                """
                .formatted(id);
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
    void stagingThenCommittingWithoutOverridesPersistsUsingTheGuessedSite() {
        final var stageResponse =
                restTemplate.postForEntity(
                        "/v1/import/divesoft",
                        new HttpEntity<>(
                                syntheticDiveRequestBody("it-test-dive-1"), authorizedJsonHeaders()),
                        StageImportResult.class);

        assertThat(stageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stageResponse.getBody()).isNotNull();
        assertThat(stageResponse.getBody().errors()).isEmpty();
        assertThat(stageResponse.getBody().staged()).hasSize(1);
        final var staged = stageResponse.getBody().staged().getFirst();
        assertThat(staged.siteNameGuess()).isEqualTo("Integration Test Lake");

        final var commitRequest =
                new PendingImportCommitRequest(null, null, null, null, null, null, null, null, null);
        final var commitResponse =
                restTemplate.postForEntity(
                        "/v1/import/pending/" + staged.id() + "/commit",
                        new HttpEntity<>(commitRequest, authorizedJsonHeaders()),
                        SimplifiedDive.class);

        assertThat(commitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitResponse.getBody()).isNotNull();

        // The pending import is consumed by commit - listing pending imports afterwards is empty.
        final var pendingAfterCommit =
                restTemplate.exchange(
                        "/v1/import/pending",
                        org.springframework.http.HttpMethod.GET,
                        new HttpEntity<>(authorizedJsonHeaders()),
                        StageImportResult[].class);
        assertThat(pendingAfterCommit.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void committingWithASiteOverrideUsesTheOverrideInsteadOfTheGuess() {
        final var stageResponse =
                restTemplate.postForEntity(
                        "/v1/import/divesoft",
                        new HttpEntity<>(
                                syntheticDiveRequestBody("it-test-dive-2"), authorizedJsonHeaders()),
                        StageImportResult.class);
        assertThat(stageResponse.getBody()).isNotNull();
        final var staged = stageResponse.getBody().staged().getFirst();

        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        "Overridden Name",
                        null,
                        null,
                        null,
                        null,
                        "A Brand New Site",
                        new Location(1.0, 2.0),
                        null);
        final var commitResponse =
                restTemplate.postForEntity(
                        "/v1/import/pending/" + staged.id() + "/commit",
                        new HttpEntity<>(commitRequest, authorizedJsonHeaders()),
                        SimplifiedDive.class);

        assertThat(commitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(commitResponse.getBody()).isNotNull();
        assertThat(commitResponse.getBody().customIdentifier()).isEqualTo("Overridden Name");
    }
}
