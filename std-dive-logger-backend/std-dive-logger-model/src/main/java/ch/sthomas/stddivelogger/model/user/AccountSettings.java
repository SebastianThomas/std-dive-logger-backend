package ch.sthomas.stddivelogger.model.user;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The signed-in account's own details for its settings page - never another user's ({@link
 * FrontendUser} is the public view). {@code pendingEmail}: an unconfirmed email change.
 */
public record AccountSettings(
        long id,
        String name,
        String email,
        boolean emailVerified,
        Instant createdAt,
        @Nullable String pendingEmail) {}
