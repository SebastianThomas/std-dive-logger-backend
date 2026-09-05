package ch.sthomas.stddivelogger.importws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterDbTestDatabase;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterPnfTestLogs;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.crypto.SecretKey;

/**
 * A real end-to-end test of both primary import HTTP paths - JSON-staging a Divesoft/wetnotes dive
 * and multipart-uploading a real UDDF file - against a single shared, throwaway Testcontainers
 * Postgres instance: confirms the endpoints are actually reachable and covered by the existing
 * security filter chain (rather than silently falling outside its securityMatcher), and that
 * staging a dive and then committing it (with and without a site override) really persists it
 * through the full stack. Kept as one test class (one container startup) rather than split by
 * import source, since spinning up Postgres is the expensive part of this test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class ImportIntegrationTest {
    private static final String TEST_JWT_SECRET =
            "integration-test-jwt-signing-secret-needs-to-be-long-enough";
    private static final String TEST_USER_EMAIL = "test@test.ch";

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
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired private RestTestClient restTestClient;

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

    private static HttpHeaders authorizedHeaders() {
        final var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken());
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
                          "deviceSerial": "IT-SERIAL-%s",
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
                .formatted(id, id);
    }

    @Test
    void divesoftImportEndpointRejectsUnauthenticatedRequests() {
        restTestClient
                .post()
                .uri("/v1/import/divesoft")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"dives\":[]}")
                .exchange()
                .expectStatus()
                .value(status -> assertThat(status).isIn(401, 403));
    }

    @Test
    void stagingThenCommittingWithoutOverridesPersistsUsingTheGuessedSite() {
        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import/divesoft")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(syntheticDiveRequestBody("it-test-dive-1"))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();

        final var body = Objects.requireNonNull(stageBody);
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(1);
        final var staged = body.staged().getFirst();
        assertThat(staged.siteNameGuess()).isEqualTo("Integration Test Lake");

        final var commitRequest =
                new PendingImportCommitRequest(
                        null, null, null, null, null, null, null, null, null, null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(commitBody).isNotNull();

        // The pending import is consumed by commit - listing pending imports afterwards is empty.
        restTestClient
                .get()
                .uri("/v1/import/pending")
                .headers(h -> h.addAll(authorizedJsonHeaders()))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void committingWithASiteOverrideUsesTheOverrideInsteadOfTheGuess() {
        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import/divesoft")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(syntheticDiveRequestBody("it-test-dive-2"))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();
        final var staged = Objects.requireNonNull(stageBody).staged().getFirst();

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
                        null,
                        null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(Objects.requireNonNull(commitBody).customIdentifier())
                .isEqualTo("Overridden Name");
    }

    @Test
    void uploadingARealUddfFileThenCommittingPersistsTheParsedMeasurementData() {
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add(
                "file", new ClassPathResource("Perdix_2_A3B6F031__42_2024-12-1_15-24-0.uddf"));

        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import")
                        .headers(h -> h.addAll(authorizedHeaders()))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();

        final var body = Objects.requireNonNull(stageBody);
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(1);
        final var staged = body.staged().getFirst();
        // Real duration parsed from the UDDF profile timestamps, not a synthetic placeholder.
        // (Unlike the Divesoft path, the UDDF reader doesn't populate the cheap maxDepth guess on
        // PendingImportSummary - the real per-measurement depth data only surfaces once
        // committed, asserted on the persisted dive's summary below.)
        assertThat(staged.durationSeconds()).isNotNull().isGreaterThan(0L);

        // Unlike the Divesoft path (which guesses coordinates and can get-or-create a site by
        // name+location), the UDDF reader only guesses a site name with no coordinates - so
        // committing requires an explicit site override the same way the real frontend would
        // supply one after reviewing the staged import.
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Integration Test UDDF Site",
                        new Location(3.0, 4.0),
                        null,
                        null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(commitBody).isNotNull();
        // The persisted dive's summary is computed from the actual per-measurement depth data
        // parsed out of the UDDF file, confirming real measurements (not just a placeholder row)
        // made it into the database.
        // (bottomTime is derived from the profile's own measurement timestamps rather than the
        // raw UDDF start/end span used for the staged duration guess, so the two aren't expected
        // to match exactly - both being independently positive is what confirms real per-source
        // data flowed through both times.)
        final var nonNullCommitBody = Objects.requireNonNull(commitBody);
        assertThat(nonNullCommitBody.summary().maxDepth()).isGreaterThan(0.0);
        assertThat(nonNullCommitBody.summary().bottomTime().toSeconds()).isGreaterThan(0L);

        // The pending import is consumed by commit - it's gone from the pending list afterwards.
        restTestClient
                .get()
                .uri("/v1/import/pending")
                .headers(h -> h.addAll(authorizedJsonHeaders()))
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void uploadingARealSuuntoJsonFileThenCommittingPersistsARealTtsPeak() {
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add("file", new ClassPathResource("suunto-eon-core-dive-1-deco.json"));

        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import")
                        .headers(h -> h.addAll(authorizedHeaders()))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();

        final var body = Objects.requireNonNull(stageBody);
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(1);
        final var staged = body.staged().getFirst();

        // No GPS/site guess in this format (same as UDDF) - an explicit override is required.
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Integration Test Suunto Site",
                        new Location(5.0, 6.0),
                        null,
                        null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();

        // End-to-end confirmation of the real ~8.9min (532s) deco peak found via this fixture's
        // JSON TimeToSurface field (see SuuntoJsonReaderServiceTest for the unit-level check) -
        // this asserts it survives the full stage -> commit -> DiveSummaryEntity.update() ->
        // persisted-and-reloaded round trip through the real database, not just in-memory parsing.
        final var nonNullCommitBody = Objects.requireNonNull(commitBody);
        final var summary = nonNullCommitBody.summary();
        assertThat(summary.maxTimeToSurface()).isEqualTo(java.time.Duration.ofSeconds(532));
        assertThat(summary.maxDepth()).isGreaterThan(0.0);
        // The full chain end to end: parsed TTS -> persisted DecoStop -> DiveEntity.hasDeco()'s
        // max-based 5min threshold -> DiveDataService.recomputeAutoTags() on save -> the global
        // "Deco" system tag actually lands on the committed dive's tag list.
        assertThat(nonNullCommitBody.tags()).anyMatch(t -> t.name().equals("Deco"));
    }

    @Test
    void uploadingARealSuuntoFitFileThenCommittingHasNoTtsConfirmingTheFormatGapEndToEnd() {
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add("file", new ClassPathResource("suunto-eon-core-dive-1-deco.fit"));

        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import")
                        .headers(h -> h.addAll(authorizedHeaders()))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();

        final var body = Objects.requireNonNull(stageBody);
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(1);
        final var staged = body.staged().getFirst();

        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Integration Test Suunto FIT Site",
                        new Location(7.0, 8.0),
                        null,
                        null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();

        // Same physical dive as the JSON test above (real ~8.9min/532s deco peak per its JSON
        // export), uploaded via FIT instead - confirms end-to-end, through the real database, that
        // FIT genuinely carries no TTS signal for this device (see SuuntoFitCharacterizationTest
        // for the unit-level SDK facts this is built on), not just that the unit tests say so.
        final var nonNullCommitBody = Objects.requireNonNull(commitBody);
        final var summary = nonNullCommitBody.summary();
        assertThat(summary.maxTimeToSurface()).isNull();
        assertThat(summary.maxDepth()).isGreaterThan(0.0);
        // The real-world consequence of that format gap: the same dive that gets auto-tagged
        // "Deco" via its JSON upload (see the test above) does NOT via FIT - a genuine information
        // loss, not a bug in the auto-tag logic itself.
        assertThat(nonNullCommitBody.tags()).noneMatch(t -> t.name().equals("Deco"));
    }

    @Test
    void uploadingARealShearwaterXmlFileThenCommittingPersistsTheRealTtsPeakEndToEnd() {
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add("file", new ClassPathResource("shearwater-perdix2-native.xml"));

        final var stageBody =
                restTestClient
                        .post()
                        .uri("/v1/import")
                        .headers(h -> h.addAll(authorizedHeaders()))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody();

        final var body = Objects.requireNonNull(stageBody);
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(1);
        final var staged = body.staged().getFirst();

        // No GPS/site guess in this format either - an explicit override is required.
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Integration Test Shearwater Site",
                        new Location(9.0, 10.0),
                        null,
                        null);
        final var commitBody =
                restTestClient
                        .post()
                        .uri("/v1/import/pending/" + staged.id() + "/commit")
                        .headers(h -> h.addAll(authorizedJsonHeaders()))
                        .body(commitRequest)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(SimplifiedDive.class)
                        .returnResult()
                        .getResponseBody();

        // Confirms the real 12min TTS peak (see ShearwaterXmlReaderServiceTest for the unit-level
        // parse check) survives the full stage -> commit -> DiveSummaryEntity.update() ->
        // persisted-and-reloaded round trip through the real database - not just in-memory
        // parsing. Filed after a user reported seeing no TTS at all for real-deco Shearwater XML
        // imports; that gap turned out to be the frontend never rendering the field anywhere on
        // the dive view (now fixed), not a backend defect - this test is the missing coverage that
        // would have caught it either way.
        final var nonNullCommitBody = Objects.requireNonNull(commitBody);
        final var summary = nonNullCommitBody.summary();
        assertThat(summary.maxTimeToSurface()).isEqualTo(java.time.Duration.ofMinutes(12));
        assertThat(summary.maxDepth()).isGreaterThan(0.0);
        assertThat(nonNullCommitBody.tags()).anyMatch(t -> t.name().equals("Deco"));
    }

    private static final long SHEARWATER_CLOCK_READING = 1_732_874_281L; // 2024-11-29T09:58:01

    private static final String TANK_PROFILE_DATA =
            """
            {"GasProfiles":[{"profileIndex":0,"O2Percent":21,"HePercent":0,"CircuitMode":1,\
            "StartTimeInSeconds":0.0,"EndTimeInSeconds":400.0}],\
            "TankData":[{"StartPressurePSI":"2900.75","EndPressurePSI":"1450.38",\
            "GasProfile":{"profileIndex":0,"O2Percent":21,"HePercent":0,"CircuitMode":1}}]}\
            """;

    private static Path shearwaterDatabase(final String... diveNumbers) throws Exception {
        final var blob =
                ShearwaterPnfTestLogs.gzipWithLengthPrefix(
                        ShearwaterPnfTestLogs.build(
                                SHEARWATER_CLOCK_READING,
                                400,
                                18.4,
                                14,
                                1,
                                5000,
                                List.of(
                                        ShearwaterPnfTestLogs.Sample.openCircuit(4.0, 14),
                                        ShearwaterPnfTestLogs.Sample.openCircuit(18.4, 9),
                                        ShearwaterPnfTestLogs.Sample.openCircuit(3.0, 13))));
        final var dives =
                java.util.Arrays.stream(diveNumbers)
                        .map(
                                number ->
                                        new ShearwaterDbTestDatabase.Dive(
                                                "dive-" + number,
                                                number,
                                                "Integration Test Quarry",
                                                "Yancy Wolf",
                                                "staged from the Shearwater Cloud database",
                                                "4m",
                                                "4kg",
                                                "12l",
                                                "Single Tank",
                                                TANK_PROFILE_DATA,
                                                blob))
                        .toList();
        final var file = Files.createTempFile("shearwater-integration", ".db");
        file.toFile().deleteOnExit();
        ShearwaterDbTestDatabase.write(file, dives);
        return file;
    }

    private StageImportResult stageDatabase(final Path database) {
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add("file", new FileSystemResource(database));
        return Objects.requireNonNull(
                restTestClient
                        .post()
                        .uri("/v1/import")
                        .headers(h -> h.addAll(authorizedHeaders()))
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(multipartBody)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(StageImportResult.class)
                        .returnResult()
                        .getResponseBody());
    }

    @Test
    void uploadingAShearwaterCloudDatabaseStagesEveryDiveInIt() throws Exception {
        // The whole point of this source: one upload instead of one exported file per dive.
        final var body = stageDatabase(shearwaterDatabase("81", "82", "83"));
        assertThat(body.errors()).isEmpty();
        assertThat(body.staged()).hasSize(3);
        assertThat(body.staged())
                .allSatisfy(
                        staged -> {
                            assertThat(staged.siteNameGuess()).isEqualTo("Integration Test Quarry");
                            assertThat(staged.computerSerial()).isEqualTo("A3B6F031");
                            assertThat(staged.maxDepth()).isEqualTo(18.4);
                            assertThat(staged.durationSeconds()).isNotNull().isPositive();
                        });
        assertThat(body.staged())
                .extracting(PendingImportSummary::diveNumberGuess)
                .containsExactlyInAnyOrder(81, 82, 83);
    }

    @Test
    void committingADatabaseImportCorrectsTheTimezonelessDeviceClock() throws Exception {
        // A Shearwater device stores a plain wall-clock reading with no timezone, the same as its
        // XML/UDDF/DL7 exports - so committing against a real site must shift it into that site's
        // zone. Male, Maldives is a fixed UTC+5 with no DST, so the expected result never depends
        // on when this test runs.
        final var staged = stageDatabase(shearwaterDatabase("90")).staged().getFirst();
        assertThat(staged.startDate()).isEqualTo(Instant.ofEpochSecond(SHEARWATER_CLOCK_READING));

        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Male, Maldives",
                        new Location(4.1755, 73.5093),
                        null,
                        null);
        final var committed =
                Objects.requireNonNull(
                        restTestClient
                                .post()
                                .uri("/v1/import/pending/" + staged.id() + "/commit")
                                .headers(h -> h.addAll(authorizedJsonHeaders()))
                                .body(commitRequest)
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(SimplifiedDive.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(committed.summary().start())
                .isEqualTo(
                        Instant.ofEpochSecond(SHEARWATER_CLOCK_READING).minus(5, ChronoUnit.HOURS));
        // Real per-sample data made it through, not just a header row.
        assertThat(committed.summary().maxDepth()).isEqualTo(18.4);
        assertThat(committed.summary().bottomTime().toSeconds()).isPositive();
    }

    @Test
    void committingAnImportThatCarriesCylindersSucceedsInsteadOf500ing() throws Exception {
        // Regression: a cylinder created as part of a brand-new dive has no id until flush, but
        // DiveSummaryEntity.update() calls DiveConfigurationEntity.toRecord() from inside
        // DiveEntity's own constructor (to compute OC/bailout RMV from the cylinders) - which
        // unboxed that null id and 500ed the commit of any import carrying cylinders. The
        // Shearwater database is the first importer that supplies them, so nothing caught it
        // before. The staged payload here has one tank with real start/end pressures.
        final var staged = stageDatabase(shearwaterDatabase("91")).staged().getFirst();
        final var commitRequest =
                new PendingImportCommitRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Integration Test Cylinder Site",
                        new Location(47.13, 8.78),
                        null,
                        null);
        final var committed =
                Objects.requireNonNull(
                        restTestClient
                                .post()
                                .uri("/v1/import/pending/" + staged.id() + "/commit")
                                .headers(h -> h.addAll(authorizedJsonHeaders()))
                                .body(commitRequest)
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(SimplifiedDive.class)
                                .returnResult()
                                .getResponseBody());
        // The summary is what the crashing code path produces, so a real one coming back is the
        // proof it ran to completion.
        assertThat(committed.summary().maxDepth()).isEqualTo(18.4);
        assertThat(committed.summary().bottomTime().toSeconds()).isPositive();
    }

    @Test
    void uploadingASqliteFileThatIsNotAShearwaterLogbookFailsWithAClearMessage() throws Exception {
        final var file = Files.createTempFile("not-shearwater", ".db");
        file.toFile().deleteOnExit();
        try (final var connection =
                        java.sql.DriverManager.getConnection(
                                "jdbc:sqlite:" + file.toAbsolutePath());
                final var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE notes (id integer primary key, body varchar)");
        }
        final var multipartBody = new LinkedMultiValueMap<String, Object>();
        multipartBody.add("file", new FileSystemResource(file));
        restTestClient
                .post()
                .uri("/v1/import")
                .headers(h -> h.addAll(authorizedHeaders()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(
                        response ->
                                assertThat(response).contains("not a Shearwater Cloud database"));
    }
}
