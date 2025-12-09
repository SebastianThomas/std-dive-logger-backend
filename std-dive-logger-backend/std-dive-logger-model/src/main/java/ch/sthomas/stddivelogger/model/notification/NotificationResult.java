package ch.sthomas.stddivelogger.model.notification;

public sealed interface NotificationResult
        permits SuccessfulNotificationResult, UnsuccessfulNotificationResult {}
