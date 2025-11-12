package ch.sthomas.stddivelogger.model.user;

import java.time.Instant;

public record User(long id, String email, String password, Instant createdAt, Instant updatedAt) {}
