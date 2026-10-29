package ch.sthomas.stddivegastracker.model.team;

/** API.md §1: one of the caller's ACTIVE teams in {@code GET /v1/me}. */
public record TeamSummary(long teamId, String name, Role role, long memberId) {}
