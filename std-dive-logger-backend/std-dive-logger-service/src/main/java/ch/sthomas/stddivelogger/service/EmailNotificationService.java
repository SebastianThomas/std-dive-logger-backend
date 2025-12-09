package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.model.notification.EmailNotificationPayload;
import ch.sthomas.stddivelogger.model.notification.EmailNotificationResult;
import ch.sthomas.stddivelogger.model.notification.NotificationPayload;
import ch.sthomas.stddivelogger.model.notification.NotificationResult;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {
    @Override
    public <T> NotificationResult sendNotification(final NotificationPayload<T> payload) {
        if (payload instanceof final EmailNotificationPayload p) {
            return sendEmail(p);
        }
        throw new IllegalArgumentException("Expeced email notification payload, got " + payload);
    }

    public EmailNotificationResult sendEmail(final EmailNotificationPayload p) {
        throw new NotImplementedException();
    }
}
