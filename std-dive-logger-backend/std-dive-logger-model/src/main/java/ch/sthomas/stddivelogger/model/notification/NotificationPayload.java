package ch.sthomas.stddivelogger.model.notification;

public sealed interface NotificationPayload<R> permits EmailNotificationPayload {
    R receiver();
}
