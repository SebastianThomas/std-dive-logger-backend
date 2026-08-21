package ch.sthomas.stddivelogger.ws.services.job;

import ch.sthomas.stddivelogger.data.repository.AccountRequestRepository;
import ch.sthomas.stddivelogger.data.repository.GroupRepository;
import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.model.user.GroupRole;
import ch.sthomas.stddivelogger.service.DivePhotoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class CleanupJob {
    private static final Logger logger = LoggerFactory.getLogger(CleanupJob.class);
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRequestRepository accountRequestRepository;
    private final GroupRepository groupRepository;
    private final DivePhotoService divePhotoService;

    public CleanupJob(
            final RefreshTokenRepository refreshTokenRepository,
            final AccountRequestRepository accountRequestRepository,
            GroupRepository groupRepository,
            final DivePhotoService divePhotoService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountRequestRepository = accountRequestRepository;
        this.groupRepository = groupRepository;
        this.divePhotoService = divePhotoService;
    }

    @Schedules({@Scheduled(cron = "0 0 3 * * *"), @Scheduled(initialDelay = 0)})
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        logger.info("Scheduled Job: Cleaning expired refresh tokens");
        refreshTokenRepository.deleteAllByExpiresAtBefore(OffsetDateTime.now());
    }

    @Schedules({@Scheduled(cron = "0 */10 * * * *"), @Scheduled(initialDelay = 0)})
    @Transactional
    public void cleanupExpiredAccountTokens() {
        logger.info("Scheduled Job: Cleaning expired account change tokens");
        final var deleted =
                accountRequestRepository.deleteAllByValidUntilBefore(
                        Instant.now().atOffset(ZoneOffset.UTC));
        logger.info("Deleted {} expired account change tokens", deleted);
    }

    @Schedules({@Scheduled(cron = "0 59 */6 * * *"), @Scheduled(initialDelay = 0)})
    @Transactional
    public void cleanupGroupsWithoutAdmin() {
        logger.info("Scheduled Job: Cleaning groups without admin");
        final var deleted = groupRepository.deleteAllByNoAdmin(GroupRole.ADMIN);
        logger.info("Deleted {} groups without admin", deleted);
    }

    @Schedules({@Scheduled(cron = "0 30 4 * * *"), @Scheduled(initialDelay = 0)})
    public void cleanupExpiredPendingPhotoUploads() {
        logger.info("Scheduled Job: Cleaning expired pending dive photo uploads");
        final var deleted = divePhotoService.expireOldPendingUploads();
        if (deleted > 0) {
            logger.info("Deleted {} expired pending dive photo upload(s)", deleted);
        }
    }
}
