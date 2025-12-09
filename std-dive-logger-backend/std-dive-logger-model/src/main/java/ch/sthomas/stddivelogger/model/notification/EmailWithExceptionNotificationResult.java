package ch.sthomas.stddivelogger.model.notification;

public record EmailWithExceptionNotificationResult(Exception e)
        implements UnsuccessfulNotificationResult {}
