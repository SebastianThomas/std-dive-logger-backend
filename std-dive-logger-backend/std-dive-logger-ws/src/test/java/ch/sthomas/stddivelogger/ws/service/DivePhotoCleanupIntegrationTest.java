package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.repository.DivePhotoRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.service.DivePhotoService;
import ch.sthomas.stddivelogger.service.DiveService;

import jakarta.persistence.EntityManager;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Coverage for the scheduled cleanup of dive-photo upload rows whose direct PUT to storage never
 * completed/got confirmed ({@code DivePhotoService.expireOldPendingUploads}, wired into {@code
 * CleanupJob}). Backdates a pending row's {@code created_at} directly (it's a
 * {@code @CreationTimestamp}, not otherwise settable) to simulate one old enough to expire.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@Transactional
class DivePhotoCleanupIntegrationTest {

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
    @Autowired private EntityManager entityManager;

    @Test
    void oldUnconfirmedUploadsAreDeletedButRecentAndConfirmedOnesAreNot() {
        final var owner =
                userRepository
                        .save(new UserEntity("photo-cleanup-it@test.ch", "hash", "Owner"))
                        .toRecord();
        final var siteId =
                diveSiteRepository
                        .save(
                                new DiveSiteEntity(
                                        "Photo Cleanup IT Site", new Location(3.0, 3.0).toPoint()))
                        .toRecord()
                        .id();
        final var diveId =
                diveService
                        .createEmptyDive(
                                owner,
                                new UploadDiveBody(
                                        1,
                                        "photo-cleanup-it",
                                        siteId,
                                        12.0,
                                        Duration.ofMinutes(15),
                                        Instant.now()))
                        .id();

        final var oldPending =
                divePhotoService.requestUploadUrl(
                        owner, diveId, new DivePhotoUploadUrlBody("image/jpeg", "old.jpg"));
        final var recentPending =
                divePhotoService.requestUploadUrl(
                        owner, diveId, new DivePhotoUploadUrlBody("image/jpeg", "recent.jpg"));
        final var confirmedButOld =
                divePhotoService.requestUploadUrl(
                        owner, diveId, new DivePhotoUploadUrlBody("image/jpeg", "confirmed.jpg"));
        divePhotoService.confirm(owner, diveId, confirmedButOld.photoId(), 123L);

        entityManager.flush();
        final var farPast = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);
        entityManager
                .createNativeQuery(
                        "UPDATE t_dive_photo SET created_at = :ts WHERE pk_photo_id IN (:ids)")
                .setParameter("ts", farPast)
                .setParameter(
                        "ids", java.util.List.of(oldPending.photoId(), confirmedButOld.photoId()))
                .executeUpdate();
        entityManager.clear();

        final var deletedCount = divePhotoService.expireOldPendingUploads();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(divePhotoRepository.findById(oldPending.photoId())).isEmpty();
        assertThat(divePhotoRepository.findById(recentPending.photoId())).isPresent();
        assertThat(divePhotoRepository.findById(confirmedButOld.photoId()))
                .isPresent(); // confirmed, so never a cleanup target regardless of age
    }
}
