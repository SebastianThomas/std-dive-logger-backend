package ch.sthomas.stddivelogger.importws.job;

import ch.sthomas.stddivelogger.service.importer.ImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingImportExpiryJob {
    private static final Logger logger = LoggerFactory.getLogger(PendingImportExpiryJob.class);
    private final ImportService importService;

    public PendingImportExpiryJob(final ImportService importService) {
        this.importService = importService;
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void expireOldPendingImports() {
        final var deleted = importService.expireOldPendingImports();
        if (deleted > 0) {
            logger.info("Expired {} pending import(s) older than 48h", deleted);
        }
    }
}
