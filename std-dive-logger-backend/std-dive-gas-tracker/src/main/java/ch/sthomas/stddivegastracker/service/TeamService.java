package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.team.AddMemberRequest;
import ch.sthomas.stddivegastracker.model.team.InviteLink;
import ch.sthomas.stddivegastracker.model.team.InvitePreview;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamInput;
import ch.sthomas.stddivegastracker.model.team.UpdateMemberRequest;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** API.md §1 (teams) and §2 (members); authorization per DOMAIN §10. */
@Service
public final class TeamService {

    private static final int INVITE_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    /**
     * By username: someone already using the app. By email: known user -> ACTIVE, unknown email ->
     * PENDING until that person signs in (DOMAIN §2).
     */
    public Member addMember(final long teamId, final GasUser user, final AddMemberRequest request) {
        teamAccess.requireAdmin(teamId, user);
        final var username = request.username();
        if (username != null && !username.isEmpty()) {
            return teamDataService.addMemberByUsername(teamId, username, request.role());
        }
        return teamDataService.addMember(
                teamId,
                UserService.normalizeEmail(Objects.requireNonNull(request.email())),
                request.role());
    }

    public InviteLink inviteLink(final long teamId, final GasUser user) {
        teamAccess.requireAdmin(teamId, user);
        return teamDataService.inviteLink(teamId);
    }

    /** A new token every time, so a link that went too far can be replaced. */
    public InviteLink resetInviteLink(final long teamId, final GasUser user) {
        teamAccess.requireAdmin(teamId, user);
        final var bytes = new byte[INVITE_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return teamDataService.setInviteToken(
                teamId, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public InviteLink turnOffInviteLink(final long teamId, final GasUser user) {
        teamAccess.requireAdmin(teamId, user);
        return teamDataService.setInviteToken(teamId, null);
    }

    public InvitePreview previewInvite(final String token, final GasUser user) {
        return teamDataService.previewInvite(token, user.userId());
    }

    /** Any signed-in user holding the link (DOMAIN §2); answers with the team to open. */
    public InvitePreview joinByInvite(final String token, final GasUser user) {
        teamDataService.joinByInvite(token, user.userId());
        return teamDataService.previewInvite(token, user.userId());
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
