package ch.sthomas.stddivegastracker.model.user;

import ch.sthomas.stddivegastracker.model.team.TeamSummary;

import java.util.List;

/** API.md §1 - {@code GET /v1/me}. */
public record MeResponse(long userId, String email, String displayName, List<TeamSummary> teams) {}
