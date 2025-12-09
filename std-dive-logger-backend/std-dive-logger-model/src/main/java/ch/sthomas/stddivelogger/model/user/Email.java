package ch.sthomas.stddivelogger.model.user;

import ch.sthomas.stddivelogger.model.notification.EmailNotificationPayload;

import java.time.Instant;
import java.util.Optional;

public record Email(
        String receiver,
        String subject,
        String content,
        Optional<Instant> sentAt,
        boolean sending) {
    public EmailNotificationPayload toPayload() {
        return new EmailNotificationPayload(receiver, subject, content);
    }
}
