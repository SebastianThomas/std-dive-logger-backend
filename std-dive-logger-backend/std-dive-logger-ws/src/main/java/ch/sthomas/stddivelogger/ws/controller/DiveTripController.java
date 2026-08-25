package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveTripDataService.DefaultTeamEntry;
import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTrip;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripDefaultTeamMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripListEntry;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripType;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveTripService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/dive-trips")
@Validated
public class DiveTripController {

    private final DiveTripService diveTripService;

    public DiveTripController(final DiveTripService diveTripService) {
        this.diveTripService = diveTripService;
    }

    @Operation(
            summary =
                    "List the current user's dive trips, ordered by each trip's own most recent"
                            + " dive (transitively) - newest first, with a dive-less trip pinned"
                            + " to the top - never by id/creation order")
    @GetMapping(path = "")
    public List<DiveTripListEntry> getTrips(@AuthenticationPrincipal final User user) {
        return diveTripService.getTripsForUser(user);
    }

    @Operation(summary = "Get a single dive trip")
    @GetMapping(path = "/{id}")
    public DiveTrip getTrip(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        return diveTripService.getTrip(user, id);
    }

    public record CreateDiveTripBody(@NotBlank String name, @NotNull DiveTripType type) {}

    @Operation(summary = "Create a new dive trip or course")
    @PostMapping(path = "")
    public DiveTrip createTrip(
            @AuthenticationPrincipal final User user,
            @Valid @NotNull @RequestBody final CreateDiveTripBody body) {
        return diveTripService.createTrip(user, body.name(), body.type());
    }

    public record UpdateDiveTripBody(
            @NotBlank String name,
            @NotNull DiveTripType type,
            @Nullable TeamTerminology teamTerminology) {}

    @Operation(summary = "Update a dive trip's name/type/terminology")
    @PutMapping(path = "/{id}")
    public DiveTrip updateTrip(
            @AuthenticationPrincipal final User user,
            @PathVariable @Positive final long id,
            @Valid @NotNull @RequestBody final UpdateDiveTripBody body) {
        return diveTripService.updateTrip(
                user, id, body.name(), body.type(), body.teamTerminology());
    }

    @Operation(summary = "Delete a dive trip")
    @DeleteMapping(path = "/{id}")
    public void deleteTrip(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        diveTripService.deleteTrip(user, id);
    }

    @Operation(summary = "List a trip's direct members (dives and sub-trips)")
    @GetMapping(path = "/{id}/members")
    public List<DiveTripMember> getMembers(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        return diveTripService.getDirectMembers(user, id);
    }

    @Operation(
            summary = "Add a dive as a direct member of a trip",
            description =
                    "seedBuddies (default true) copies the trip's default team roster onto the"
                            + " dive's named buddies, but only if the dive doesn't already have any.")
    @PostMapping(path = "/{id}/members/dives/{diveId}")
    public void addDiveMember(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @PathVariable @Positive final long diveId,
            @RequestParam(value = "seedBuddies", defaultValue = "true") final boolean seedBuddies) {
        diveTripService.addDiveMember(user, id, diveId, seedBuddies);
    }

    @Operation(summary = "Remove a dive from a trip")
    @DeleteMapping(path = "/{id}/members/dives/{diveId}")
    public void removeDiveMember(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @PathVariable @Positive final long diveId) {
        diveTripService.removeDiveMember(user, id, diveId);
    }

    @Operation(
            summary = "Add a sub-trip as a direct member of a trip",
            description = "Rejected with 400 if it would create a cycle (both trips must nest).")
    @PostMapping(path = "/{id}/members/trips/{childTripId}")
    public void addTripMember(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @PathVariable @Positive final long childTripId) {
        diveTripService.addTripMember(user, id, childTripId);
    }

    @Operation(summary = "Remove a sub-trip from a trip")
    @DeleteMapping(path = "/{id}/members/trips/{childTripId}")
    public void removeTripMember(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @PathVariable @Positive final long childTripId) {
        diveTripService.removeTripMember(user, id, childTripId);
    }

    @Operation(summary = "List every dive transitively under this trip, including via sub-trips")
    @GetMapping(path = "/{id}/dives")
    public PagedResponse<BasicDiveInfo> getTransitiveDives(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero final int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Positive final int pageSize) {
        return diveTripService.getTransitiveDives(user, id, page, pageSize);
    }

    @Operation(summary = "Get a trip's default team roster")
    @GetMapping(path = "/{id}/default-team")
    public List<DiveTripDefaultTeamMember> getDefaultTeam(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        return diveTripService.getDefaultTeam(user, id);
    }

    public record DefaultTeamEntryBody(
            @Nullable @Positive Long buddyUserId,
            @Nullable String buddyName,
            @NotNull BuddyRole role) {
        public DefaultTeamEntryBody {
            // Normalize a blank name to null up front, before the exactly-one-of check below, so
            // {buddyUserId: 5, buddyName: " "} can't slip through as "has both" (the check would
            // otherwise treat a blank string as absent for the check but not for what actually
            // gets persisted, then fail the DB's own exactly-one-of CHECK constraint with a 500
            // instead of a clean 400 here).
            buddyName = buddyName != null && buddyName.isBlank() ? null : buddyName;
            if ((buddyUserId == null) == (buddyName == null)) {
                throw new IllegalArgumentException(
                        "A default team entry must have exactly one of buddyUserId/buddyName.");
            }
        }
    }

    @Operation(
            summary = "Replace a trip's default team roster",
            description = "This is a prefill template only - it never touches dives already added.")
    @PutMapping(path = "/{id}/default-team")
    public List<DiveTripDefaultTeamMember> replaceDefaultTeam(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @Valid @NotNull @RequestBody final List<DefaultTeamEntryBody> body) {
        return diveTripService.replaceDefaultTeam(
                user,
                id,
                body.stream()
                        .map(e -> new DefaultTeamEntry(e.buddyUserId(), e.buddyName(), e.role()))
                        .toList());
    }

    @Operation(summary = "List every trip that directly contains this dive")
    @GetMapping(path = "/for-dive/{diveId}")
    public List<DiveTrip> getTripsContainingDive(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long diveId) {
        return diveTripService.getTripsContainingDive(user, diveId);
    }
}
