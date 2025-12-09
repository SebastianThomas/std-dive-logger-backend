package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.model.notification.NotificationPayload;
import ch.sthomas.stddivelogger.model.notification.NotificationResult;

public interface NotificationService {
    <T> NotificationResult sendNotification(NotificationPayload<T> payload);
}
