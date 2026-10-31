package ch.sthomas.stddivegastracker.model.user;

import org.jspecify.annotations.Nullable;

/**
 * The authenticated user: {@code userId} = dive-logger user id (JWT {@code uid}), {@code
 * displayName} = the gas-tracker name, {@code username} = the dive logger's, if known yet.
 */
public record GasUser(long userId, String displayName, @Nullable String username) {}
