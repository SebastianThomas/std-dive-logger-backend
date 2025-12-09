package ch.sthomas.stddivelogger.model.notification;

import java.time.Instant;

public record EmailNotificationResult(Instant time) implements SuccessfulNotificationResult {}
