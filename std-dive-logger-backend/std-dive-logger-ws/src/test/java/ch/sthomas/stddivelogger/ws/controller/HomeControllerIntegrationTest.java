package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import java.util.UUID;

import javax.crypto.SecretKey;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
class HomeControllerIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "home-controller-it-jwt-signing-secret-needs-to-be-long-enough";

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
                () -> "home-controller-it-jwt-refresh-secret-needs-to-be-long-enough");
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

    private static String bearerToken(final String email) {
        final SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }

    private User seedUser(final String name) {
        return userRepository
                .save(new UserEntity("home-ctl-it-" + UUID.randomUUID() + "@test.ch", "h", name))
                .toRecord();
    }

    private long seedSite() {
        return diveSiteRepository
                .save(
                        new DiveSiteEntity(
                                "Home Ctl IT " + UUID.randomUUID(),
                                new Location(47.0, 8.0).toPoint()))
                .toRecord()
                .id();
    }

    private void seedDive(
            final User owner,
            final long siteId,
            final int number,
            final double maxDepth,
            final Duration duration,
            final Instant start) {
        diveService.createEmptyDive(
                owner,
                new UploadDiveBody(number, "home-ctl-it", siteId, maxDepth, duration, start));
    }

    @Test
    void anonymousGetReturns401() {
        restTestClient.get().uri("/v1/home").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void returnsTheCallersOwnTotalsAndName() {
        final var owner = seedUser("Dashboard Diver");
        final var siteId = seedSite();
        seedDive(
                owner,
                siteId,
                1,
                18.0,
                Duration.ofMinutes(40),
                Instant.parse("2026-05-01T09:00:00Z"));
        seedDive(
                owner,
                siteId,
                2,
                32.5,
                Duration.ofMinutes(55),
                Instant.parse("2026-06-01T09:00:00Z"));

        restTestClient
                .get()
                .uri("/v1/home")
                .header("Authorization", "Bearer " + bearerToken(owner.email()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userName")
                .isEqualTo("Dashboard Diver")
                .jsonPath("$.diveCount")
                .isEqualTo(2)
                .jsonPath("$.maxDiveNumber")
                .isEqualTo(2)
                .jsonPath("$.maxDepth")
                .isEqualTo(32.5)
                .jsonPath("$.records.deepest.diveNumber")
                .isEqualTo(2)
                .jsonPath("$.recentDives.length()")
                .isEqualTo(2);
    }

    @Test
    void doesNotExposeAnotherUsersDives() {
        final var alice = seedUser("Alice");
        final var bob = seedUser("Bob");
        final var siteId = seedSite();
        seedDive(
                alice,
                siteId,
                1,
                12.0,
                Duration.ofMinutes(30),
                Instant.parse("2026-04-01T09:00:00Z"));
        seedDive(
                bob,
                siteId,
                1,
                90.0,
                Duration.ofMinutes(120),
                Instant.parse("2026-04-02T09:00:00Z"));

        restTestClient
                .get()
                .uri("/v1/home")
                .header("Authorization", "Bearer " + bearerToken(alice.email()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.diveCount")
                .isEqualTo(1)
                .jsonPath("$.maxDepth")
                .isEqualTo(12.0);
    }
}
