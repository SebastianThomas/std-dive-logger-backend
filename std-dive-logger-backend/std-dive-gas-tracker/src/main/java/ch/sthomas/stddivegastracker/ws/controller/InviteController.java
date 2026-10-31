package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.team.InviteLink;
import ch.sthomas.stddivegastracker.model.team.InvitePreview;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.TeamService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API.md §2 "Invite links": admins manage a team's link, anyone signed in can use it. */
@RestController
@RequestMapping("/v1")
public final class InviteController {

    private final TeamService teamService;

    public InviteController(final TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/teams/{teamId}/invite-link")
    public InviteLink inviteLink(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return teamService.inviteLink(teamId, user);
    }

    @PostMapping("/teams/{teamId}/invite-link")
    public InviteLink resetInviteLink(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return teamService.resetInviteLink(teamId, user);
    }

    @DeleteMapping("/teams/{teamId}/invite-link")
    public InviteLink turnOffInviteLink(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return teamService.turnOffInviteLink(teamId, user);
    }

    @GetMapping("/invites/{token}")
    public InvitePreview preview(
            @AuthenticationPrincipal final GasUser user, @PathVariable final String token) {
        return teamService.previewInvite(token, user);
    }

    @PostMapping("/invites/{token}/join")
    public InvitePreview join(
            @AuthenticationPrincipal final GasUser user, @PathVariable final String token) {
        return teamService.joinByInvite(token, user);
    }
}
