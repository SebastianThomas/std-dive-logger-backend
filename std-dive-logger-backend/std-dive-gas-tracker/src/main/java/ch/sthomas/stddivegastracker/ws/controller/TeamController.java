package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamInput;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.TeamService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API.md §1 "Me and teams" (the {@code /v1/me} half is {@link MeController}). */
@RestController
@RequestMapping("/v1/teams")
public final class TeamController {

    private final TeamService teamService;

    public TeamController(final TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public Team create(
            @AuthenticationPrincipal final GasUser user, @RequestBody final TeamInput input) {
        return teamService.create(user, input);
    }

    @GetMapping("/{teamId}")
    public Team get(@AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return teamService.get(teamId, user);
    }

    @PutMapping("/{teamId}")
    public Team update(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final TeamInput input) {
        return teamService.update(teamId, user, input);
    }
}
