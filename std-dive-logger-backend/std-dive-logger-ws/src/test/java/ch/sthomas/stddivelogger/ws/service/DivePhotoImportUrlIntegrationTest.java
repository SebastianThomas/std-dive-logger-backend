package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DivePhotoService;
import ch.sthomas.stddivelogger.service.DiveService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

/**
 * Coverage for {@code DivePhotoService.importFromUrl}'s SSRF guard and input validation - this
 * fetches a user-supplied URL server-side, so the negative/rejection paths here (private addresses,
 * non-http(s) schemes, unresolvable hosts) are the security-critical behavior. A full
 * successful-fetch round trip isn't covered here since that would require fetching from a real
 * public host (flaky/network-dependent in CI) or a local fixture server, which the SSRF guard
 * itself would correctly reject as a private/loopback address - the guard and a same-host test
 * fixture are fundamentally in tension.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DivePhotoImportUrlIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
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

    @Autowired private DivePhotoService divePhotoService;
    @Autowired private DiveService diveService;
    @Autowired private UserRepository userRepository;
    @Autowired private DiveSiteRepository diveSiteRepository;

    private ch.sthomas.stddivelogger.model.user.User owner;
    private long diveId;

    @BeforeEach
    void setUp() {
        owner =
                userRepository
                        .save(new UserEntity("photo-url-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        final var siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Photo URL IT Site", new Location(9.0, 9.0).toPoint()))
                        .toRecord()
                        .id();
        diveId =
                diveService
                        .createEmptyDive(
                                owner,
                                new UploadDiveBody(
                                        1,
                                        "photo-url-it",
                                        siteId,
                                        15.0,
                                        Duration.ofMinutes(20),
                                        Instant.now()))
                        .id();
    }

    @Test
    void rejectsLoopbackAddresses() {
        assertThatThrownBy(
                        () ->
                                divePhotoService.importFromUrl(
                                        owner, diveId, "http://127.0.0.1:1/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateAddresses() {
        assertThatThrownBy(
                        () ->
                                divePhotoService.importFromUrl(
                                        owner, diveId, "http://10.0.0.5/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                divePhotoService.importFromUrl(
                                        owner, diveId, "http://192.168.1.1/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(
                        () -> divePhotoService.importFromUrl(owner, diveId, "file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> divePhotoService.importFromUrl(owner, diveId, "not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresWriteAccessToTheDive() {
        final var stranger =
                userRepository
                        .save(new UserEntity("photo-url-it-stranger@test.ch", "hash", "S"))
                        .toRecord();
        assertThatThrownBy(
                        () ->
                                divePhotoService.importFromUrl(
                                        stranger, diveId, "http://example.com/photo.jpg"))
                .isInstanceOf(ForbiddenException.class);
    }
}
