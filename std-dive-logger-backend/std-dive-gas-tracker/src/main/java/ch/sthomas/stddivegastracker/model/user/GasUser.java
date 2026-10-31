package ch.sthomas.stddivegastracker.model.user;

import org.jspecify.annotations.Nullable;

/**
 * The authenticated user: {@code userId} = dive-logger user id (JWT {@code uid}), {@code email} =
 * the token's current address (lower-cased), {@code displayName} = the gas-tracker-local name.
 */
public record GasUser(long userId, String email, String displayName, @Nullable String username) {}
