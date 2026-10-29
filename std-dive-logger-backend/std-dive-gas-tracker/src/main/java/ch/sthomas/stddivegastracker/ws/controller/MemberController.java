package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.team.AddMemberRequest;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.UpdateMemberRequest;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.TeamService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API.md §2 "Members". */
@RestController
@RequestMapping("/v1/teams/{teamId}/members")
public final class MemberController {

    private final TeamService teamService;

    public MemberController(final TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public List<Member> list(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return teamService.members(teamId, user);
    }

    @PostMapping
    public Member add(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final AddMemberRequest request) {
        return teamService.addMember(teamId, user, request);
    }

    @PutMapping("/{memberId}")
    public Member update(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long memberId,
            @RequestBody final UpdateMemberRequest request) {
        return teamService.updateMember(teamId, memberId, user, request);
    }

    @PostMapping("/me/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        teamService.leave(teamId, user);
    }
}
