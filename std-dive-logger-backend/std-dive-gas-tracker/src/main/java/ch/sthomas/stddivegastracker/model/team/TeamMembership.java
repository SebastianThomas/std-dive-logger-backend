package ch.sthomas.stddivegastracker.model.team;

/** The caller's ACTIVE membership in a team, as returned by {@code TeamAccessService}. */
public record TeamMembership(long memberId, long teamId, Role role) {}
