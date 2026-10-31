package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

/**
 * API.md §2: a team's working invite token, absent when it has none. {@code canTurnBackOn}: the
 * link is off, and its last token can be turned on again.
 */
public record InviteLink(@Nullable String token, boolean canTurnBackOn) {}
