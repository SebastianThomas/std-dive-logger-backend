package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * DOMAIN §10. Every team-scoped service method starts with {@link #requireMember} or {@link
 * #requireAdmin}; both return the caller's membership. Unknown team, non-member and LEFT member all
 * look the same (404), so team existence is never leaked.
 */
@Service
public final class TeamAccessService {

    private final TeamDataService teamDataService;

    public TeamAccessService(final TeamDataService teamDataService) {
        this.teamDataService = teamDataService;
    }

    /** 404 unless {@code user} is an ACTIVE member of the team. */
    public TeamMembership requireMember(final long teamId, final GasUser user) {
        return teamDataService
                .findActiveMembership(teamId, user.userId())
                .orElseThrow(() -> new NoSuchElementException("Team not found."));
    }

    /** 404 unless an ACTIVE member, 403 unless that member is an ADMIN. */
    public TeamMembership requireAdmin(final long teamId, final GasUser user) {
        final var membership = requireMember(teamId, user);
        if (membership.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only team admins can do this.");
        }
        return membership;
    }
}
