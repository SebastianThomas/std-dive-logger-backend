package ch.sthomas.stddivelogger.ws.services.job;

import ch.sthomas.stddivelogger.data.repository.AccountRequestRepository;
import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;

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
public class RefreshTokenCleanupJob {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRequestRepository accountRequestRepository;

    public RefreshTokenCleanupJob(
            final RefreshTokenRepository refreshTokenRepository,
            final AccountRequestRepository accountRequestRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountRequestRepository = accountRequestRepository;
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
        accountRequestRepository.deleteAllByValidUntilBefore(
                Instant.now().atOffset(ZoneOffset.UTC));
    }
}
