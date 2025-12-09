package ch.sthomas.stddivelogger.model.notification;

public sealed interface UnsuccessfulNotificationResult extends NotificationResult
        permits EmailWithExceptionNotificationResult {}
