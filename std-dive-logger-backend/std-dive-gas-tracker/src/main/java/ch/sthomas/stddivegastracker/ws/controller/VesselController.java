package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.vessel.CloseVesselRequest;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.model.vessel.ReadingInput;
import ch.sthomas.stddivegastracker.model.vessel.VesselCreate;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.model.vessel.VesselStatusFilter;
import ch.sthomas.stddivegastracker.model.vessel.VesselSummary;
import ch.sthomas.stddivegastracker.model.vessel.VesselUpdate;
import ch.sthomas.stddivegastracker.service.VesselService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API.md §3 "Vessels (banks)". */
@RestController
@RequestMapping("/v1/teams/{teamId}/vessels")
public final class VesselController {

    private final VesselService vesselService;

    public VesselController(final VesselService vesselService) {
        this.vesselService = vesselService;
    }

    @GetMapping
    public List<VesselSummary> list(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestParam(defaultValue = "IN_SERVICE") final VesselStatusFilter status) {
        return vesselService.list(teamId, user, status);
    }

    @PostMapping
    public VesselDetail create(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final VesselCreate request) {
        return vesselService.create(teamId, user, request);
    }

    @GetMapping("/{vesselId}")
    public VesselDetail get(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId) {
        return vesselService.get(teamId, vesselId, user);
    }

    @PutMapping("/{vesselId}")
    public VesselDetail update(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId,
            @RequestBody final VesselUpdate request) {
        return vesselService.update(teamId, vesselId, user, request);
    }

    @PostMapping("/{vesselId}/close")
    public VesselDetail close(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId,
            @RequestBody final CloseVesselRequest request) {
        return vesselService.close(teamId, vesselId, user, request);
    }

    @PostMapping("/{vesselId}/reopen")
    public VesselDetail reopen(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId) {
        return vesselService.reopen(teamId, vesselId, user);
    }

    @PostMapping("/{vesselId}/cost-lines")
    public VesselDetail addCostLine(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId,
            @RequestBody final CostLineInput request) {
        return vesselService.addCostLine(teamId, vesselId, user, request);
    }

    @DeleteMapping("/{vesselId}/cost-lines/{costLineId}")
    public VesselDetail deleteCostLine(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId,
            @PathVariable final long costLineId) {
        return vesselService.deleteCostLine(teamId, vesselId, costLineId, user);
    }

    @PostMapping("/{vesselId}/readings")
    public VesselDetail addReading(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long vesselId,
            @RequestBody final ReadingInput request) {
        return vesselService.addReading(teamId, vesselId, user, request);
    }
}
