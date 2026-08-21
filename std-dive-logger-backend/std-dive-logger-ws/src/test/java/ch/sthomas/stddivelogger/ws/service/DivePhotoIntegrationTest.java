package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.data.repository.DivePhotoRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.data.service.storage.ObjectStorageService;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DivePhotoService;
import ch.sthomas.stddivelogger.service.DiveService;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Covers the full dive photo gallery flow (WS4): request-upload-url -> confirm -> list ->
 * download-proxy -> delete, using the real {@code FileStorageService} (local disk, active by
 * default per {@code application.properties}'s {@code local-output} profile) to simulate the
 * client's direct PUT between requesting the URL and confirming. Also covers the one new
 * authorization-gated streaming endpoint this app didn't have precedent for: a user with no read
 * access to the dive must not be able to fetch a photo through the download proxy.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DivePhotoIntegrationTest {

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
    @Autowired private DivePhotoRepository divePhotoRepository;
    @Autowired private ObjectStorageService storageService;

    @Test
    void fullUploadConfirmListDownloadDeleteFlow() throws IOException {
        final var owner =
                userRepository
                        .save(new UserEntity("photo-it-owner@test.ch", "hash", "Owner"))
                        .toRecord();
        final var other =
                userRepository
                        .save(new UserEntity("photo-it-other@test.ch", "hash", "Other"))
                        .toRecord();
        final var site =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Photo IT Site", new Location(47.0, 8.0).toPoint()))
                        .toRecord();
        final var dive =
                diveService.createEmptyDive(
                        owner,
                        new UploadDiveBody(
                                null,
                                "photo-it",
                                site.id(),
                                18.0,
                                Duration.ofMinutes(40),
                                Instant.parse("2026-06-02T09:00:00Z")));

        // No photos yet.
        assertThat(divePhotoService.list(owner, dive.id())).isEmpty();

        // 1. Request an upload URL - creates a pending (unconfirmed) row up front.
        final var uploadUrlResponse =
                divePhotoService.requestUploadUrl(
                        owner, dive.id(), new DivePhotoUploadUrlBody("image/jpeg", "reef.jpg"));
        assertThat(uploadUrlResponse.photoId()).isPositive();
        assertThat(uploadUrlResponse.uploadUrl()).isNotBlank();

        // Pending photos aren't listed yet.
        assertThat(divePhotoService.list(owner, dive.id())).isEmpty();

        // 2. Simulate the frontend's direct PUT to storage (local disk here).
        final var photoEntity =
                divePhotoRepository
                        .findByIdAndDive_Id(uploadUrlResponse.photoId(), dive.id())
                        .orElseThrow();
        final var bytes = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        storageService.upload(
                photoEntity.getStoragePath(),
                new ByteArrayInputStream(bytes),
                "image/jpeg",
                bytes.length);

        // 3. Confirm.
        final var confirmed =
                divePhotoService.confirm(
                        owner, dive.id(), uploadUrlResponse.photoId(), bytes.length);
        assertThat(confirmed.confirmed()).isTrue();
        assertThat(confirmed.byteSize()).isEqualTo(bytes.length);
        assertThat(confirmed.contentType()).isEqualTo("image/jpeg");
        assertThat(confirmed.uploadedByUserId()).isEqualTo(owner.id());

        // 4. List - now visible.
        final var listed = divePhotoService.list(owner, dive.id());
        assertThat(listed).extracting("id").containsExactly(uploadUrlResponse.photoId());

        // 5. Download proxy - correct bytes and content type.
        final var downloaded =
                divePhotoService.download(owner, dive.id(), uploadUrlResponse.photoId());
        assertThat(downloaded.contentType()).isEqualTo("image/jpeg");
        assertThat(downloaded.stream().readAllBytes()).isEqualTo(bytes);

        // Negative: a user with no read access to the dive gets refused by the download proxy.
        assertThatThrownBy(
                        () ->
                                divePhotoService.download(
                                        other, dive.id(), uploadUrlResponse.photoId()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> divePhotoService.list(other, dive.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(
                        () ->
                                divePhotoService.requestUploadUrl(
                                        other,
                                        dive.id(),
                                        new DivePhotoUploadUrlBody("image/png", "x.png")))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(
                        () ->
                                divePhotoService.delete(
                                        other, dive.id(), uploadUrlResponse.photoId()))
                .isInstanceOf(ForbiddenException.class);

        // 6. Delete.
        divePhotoService.delete(owner, dive.id(), uploadUrlResponse.photoId());
        assertThat(divePhotoService.list(owner, dive.id())).isEmpty();
        assertThatThrownBy(
                        () ->
                                divePhotoService.download(
                                        owner, dive.id(), uploadUrlResponse.photoId()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
