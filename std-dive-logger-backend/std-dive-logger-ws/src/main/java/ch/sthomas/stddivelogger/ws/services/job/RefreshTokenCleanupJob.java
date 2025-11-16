package ch.sthomas.stddivelogger.ws.services.job;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class RefreshTokenCleanupJob {
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(final RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(OffsetDateTime.now());
    }
}
