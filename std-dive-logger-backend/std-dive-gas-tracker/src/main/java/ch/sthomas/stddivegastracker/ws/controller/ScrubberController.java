package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.scrubber.CanisterCreate;
import ch.sthomas.stddivegastracker.model.scrubber.MarkEmptyRequest;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberCanister;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberUseInput;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.service.ScrubberService;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API.md §7 "Scrubber": every write answers with the whole canister. */
@RestController
@RequestMapping("/v1/teams/{teamId}/scrubbers")
public final class ScrubberController {

    private final ScrubberService scrubberService;

    public ScrubberController(final ScrubberService scrubberService) {
        this.scrubberService = scrubberService;
    }

    @GetMapping
    public List<ScrubberCanister> list(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return scrubberService.list(teamId, user);
    }

    @PostMapping
    public ScrubberCanister create(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final CanisterCreate request) {
        return scrubberService.create(teamId, user, request);
    }

    @GetMapping("/{canisterId}")
    public ScrubberCanister get(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId) {
        return scrubberService.get(teamId, canisterId, user);
    }

    @PostMapping("/{canisterId}/uses")
    public ScrubberCanister addUse(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId,
            @RequestBody final ScrubberUseInput request) {
        return scrubberService.addUse(teamId, canisterId, user, request);
    }

    @DeleteMapping("/{canisterId}/uses/{useId}")
    public ScrubberCanister deleteUse(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId,
            @PathVariable final long useId) {
        return scrubberService.deleteUse(teamId, canisterId, useId, user);
    }

    @PostMapping("/{canisterId}/empty")
    public ScrubberCanister markEmpty(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId,
            @RequestBody(required = false) final @Nullable MarkEmptyRequest request) {
        return scrubberService.markEmpty(teamId, canisterId, user, request);
    }

    @PostMapping("/{canisterId}/reopen")
    public ScrubberCanister reopen(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId) {
        return scrubberService.reopen(teamId, canisterId, user);
    }

    @PostMapping("/{canisterId}/cost-lines")
    public ScrubberCanister addCostLine(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId,
            @RequestBody final CostLineInput request) {
        return scrubberService.addCostLine(teamId, canisterId, user, request);
    }

    @DeleteMapping("/{canisterId}/cost-lines/{costLineId}")
    public ScrubberCanister deleteCostLine(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long canisterId,
            @PathVariable final long costLineId) {
        return scrubberService.deleteCostLine(teamId, canisterId, costLineId, user);
    }
}
