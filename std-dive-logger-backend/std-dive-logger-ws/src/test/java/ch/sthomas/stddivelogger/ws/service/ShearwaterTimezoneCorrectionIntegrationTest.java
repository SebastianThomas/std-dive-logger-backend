package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Real end-to-end check that a Shearwater native XML import's timestamps land in the dive site's
 * real timezone rather than the literal (and almost always wrong) UTC the raw file's plain
 * wall-clock reading gets parsed as at stage time - the concrete motivating gap this covers, since
 * this format carries no GPS/timezone of its own (see ImportService.correctForUnknownTimezone).
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class ShearwaterTimezoneCorrectionIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("ch.sthomas.stddivelogger.ws.jwt-secret", () -> "test-jwt-secret");
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret", () -> "test-jwt-refresh-secret");
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

    @Autowired private ImportService importService;
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;

    private User createTestUser(final String email) {
        return userRepository.save(new UserEntity(email, "hash", "IT")).toRecord();
    }

    private static MockMultipartFile fixture(final String filename) throws IOException {
        try (final var in =
                ShearwaterTimezoneCorrectionIntegrationTest.class
                        .getClassLoader()
                        .getResourceAsStream(filename)) {
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/octet-stream",
                    Objects.requireNonNull(in).readAllBytes());
        }
    }

    @Test
    void shearwaterXmlImportIsCorrectedToTheDiveSiteRealTimezoneNotLeftAsRawUtc()
            throws IOException {
        final var user = createTestUser("shearwater-tz-it-1@test.ch");
        final var staged =
                importService.stageUpload(user, List.of(fixture("shearwater-perdix2-native.xml")));
        assertThat(staged.errors()).isEmpty();
        final var pendingId = staged.staged().getFirst().id();

        // Malé, Maldives - a real, always-UTC+5, no-DST location, chosen so the expected
        // correction is unambiguous and doesn't depend on the test's own run date.
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
        final var committedDive = importService.commit(user, pendingId, commitRequest);
        final var fullDive = diveService.getDiveById(user, committedDive.id()).orElseThrow();
        final var profile = fullDive.profiles().getFirst();

        // The raw file's own startDate is "8/22/2026 10:13:49 AM" with no timezone of its own -
        // naively parsed as UTC at stage time (a placeholder), the wrong answer for a dive
        // actually made in the Maldives (UTC+5). The corrected instant is that same wall-clock
        // reading, in UTC+5.
        final var expectedStart = Instant.parse("2026-08-22T05:13:49Z");
        assertThat(profile.start()).isEqualTo(expectedStart);
        assertThat(profile.start()).isNotEqualTo(Instant.parse("2026-08-22T10:13:49Z"));
        // The last raw record's currentTime is 4025000ms (4025s) after start - duration itself
        // must be unchanged by the correction, only the absolute placement shifts.
        assertThat(profile.end()).isEqualTo(expectedStart.plusSeconds(4025));
    }
}
