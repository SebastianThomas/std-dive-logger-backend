package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.team.AddMemberRequest;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamInput;
import ch.sthomas.stddivegastracker.model.team.UpdateMemberRequest;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.stereotype.Service;

import java.util.List;

/** API.md §1 (teams) and §2 (members); authorization per DOMAIN §10. */
@Service
public final class TeamService {

    private final TeamAccessService teamAccess;
    private final TeamDataService teamDataService;

    public TeamService(final TeamAccessService teamAccess, final TeamDataService teamDataService) {
        this.teamAccess = teamAccess;
        this.teamDataService = teamDataService;
    }

    public Team create(final GasUser user, final TeamInput input) {
        return teamDataService.createTeam(user.userId(), input);
    }

    public Team get(final long teamId, final GasUser user) {
        return teamDataService.getTeam(teamAccess.requireMember(teamId, user));
    }

    public Team update(final long teamId, final GasUser user, final TeamInput input) {
        return teamDataService.updateTeam(teamAccess.requireAdmin(teamId, user), input);
    }

    public List<Member> members(final long teamId, final GasUser user) {
        teamAccess.requireMember(teamId, user);
        return teamDataService.members(teamId);
    }

    /** Known user -> ACTIVE, unknown email -> PENDING until that person signs in (DOMAIN §2). */
    public Member addMember(final long teamId, final GasUser user, final AddMemberRequest request) {
        teamAccess.requireAdmin(teamId, user);
        return teamDataService.addMember(
                teamId, UserService.normalizeEmail(request.email()), request.role());
    }

    public Member updateMember(
            final long teamId,
            final long memberId,
            final GasUser user,
            final UpdateMemberRequest request) {
        teamAccess.requireAdmin(teamId, user);
        return teamDataService.updateMember(teamId, memberId, request.role(), request.status());
    }

    /** Any member may leave, except the last ADMIN (409, DOMAIN §10). */
    public void leave(final long teamId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        teamDataService.updateMember(teamId, caller.memberId(), null, MemberStatus.LEFT);
    }
}
