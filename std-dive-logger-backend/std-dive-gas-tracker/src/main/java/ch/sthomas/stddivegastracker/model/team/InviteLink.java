package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

/** API.md §2: a team's current invite token; absent when it has no link. */
public record InviteLink(@Nullable String token) {}
