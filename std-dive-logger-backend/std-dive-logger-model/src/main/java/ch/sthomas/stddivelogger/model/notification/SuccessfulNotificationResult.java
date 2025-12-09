package ch.sthomas.stddivelogger.model.notification;

public sealed interface SuccessfulNotificationResult extends NotificationResult
        permits EmailNotificationResult {}
