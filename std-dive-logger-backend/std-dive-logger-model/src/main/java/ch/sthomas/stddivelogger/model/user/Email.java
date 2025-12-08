package ch.sthomas.stddivelogger.model.user;

import java.time.Instant;

public record Email(
        String receiver, String subject, String content, Instant sentAt, boolean sending) {}
