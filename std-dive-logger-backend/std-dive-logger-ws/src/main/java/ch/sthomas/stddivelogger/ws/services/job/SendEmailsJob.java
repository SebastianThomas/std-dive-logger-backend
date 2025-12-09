package ch.sthomas.stddivelogger.ws.services.job;

import ch.sthomas.stddivelogger.service.EmailNotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SendEmailsJob {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailsJob.class);
    private final EmailNotificationService emailNotificationService;

    public SendEmailsJob(final EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @Scheduled(cron = "*/2 * * * * *")
    public void sendMails() {
        final var sent = emailNotificationService.sendOutstandingEmails();
        logger.info("Sent {}/{} emails", sent.getRight(), sent.getLeft());
    }
}
