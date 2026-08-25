package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.service.DiveTripDataService;
import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTrip;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripDefaultTeamMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripListEntry;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripType;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Trips are single-owner (no sharing, unlike dives' reader/group model) - every write requires
 * {@code user} to be the trip's own owner, checked here rather than left to the data layer so a
 * missing check can't silently leak into a new endpoint later.
 */
@Service
public class DiveTripService {

    private final DiveTripDataService diveTripDataService;
    private final DiveService diveService;

    public DiveTripService(
            final DiveTripDataService diveTripDataService, final DiveService diveService) {
        this.diveTripDataService = diveTripDataService;
        this.diveService = diveService;
    }

    private DiveTrip requireOwnedTrip(final User user, final long tripId) {
        final var trip =
                diveTripDataService
                        .findTripById(tripId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Could not find dive trip " + tripId));
        if (trip.ownerUserId() != user.id()) {
            throw ForbiddenException.forDiveTrip(user, tripId);
        }
        return trip;
    }

    /**
     * Ordered by each trip's own most recent dive (transitively, including nested sub-trips),
     * newest first, with a trip that has no dives logged under it yet pinned to the very top -
     * never by database id/creation order, since a trip entered into the app after the fact for
     * dives from months ago shouldn't rank above one you're actively logging into today. See {@code
     * DiveTripDataService.findTripsByOwnerWithDateRange}.
     */
    public List<DiveTripListEntry> getTripsForUser(final User user) {
        return diveTripDataService.findTripsByOwnerWithDateRange(user.id());
    }

    public DiveTrip getTrip(final User user, final long tripId) {
        return requireOwnedTrip(user, tripId);
    }

    public DiveTrip createTrip(final User user, final String name, final DiveTripType type) {
        return diveTripDataService.createTrip(user.id(), name, type);
    }

    public DiveTrip updateTrip(
            final User user,
            final long tripId,
            final String name,
            final DiveTripType type,
            @Nullable final TeamTerminology teamTerminology) {
        requireOwnedTrip(user, tripId);
        return diveTripDataService.updateTrip(tripId, name, type, teamTerminology);
    }

    public void deleteTrip(final User user, final long tripId) {
        requireOwnedTrip(user, tripId);
        diveTripDataService.deleteTrip(tripId);
    }

    public List<DiveTripMember> getDirectMembers(final User user, final long tripId) {
        requireOwnedTrip(user, tripId);
        return diveTripDataService.findDirectMembers(tripId);
    }

    public void addDiveMember(
            final User user, final long tripId, final long diveId, final boolean seedBuddies) {
        requireOwnedTrip(user, tripId);
        if (!diveService.hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        diveTripDataService.addDiveMember(tripId, diveId);
        if (seedBuddies) {
            diveTripDataService.seedDiveBuddiesFromDefaultTeam(tripId, diveId);
        }
    }

    public void removeDiveMember(final User user, final long tripId, final long diveId) {
        requireOwnedTrip(user, tripId);
        diveTripDataService.removeDiveMember(tripId, diveId);
    }

    public void addTripMember(final User user, final long tripId, final long childTripId) {
        requireOwnedTrip(user, tripId);
        requireOwnedTrip(user, childTripId);
        diveTripDataService.addTripMember(tripId, childTripId);
    }

    public void removeTripMember(final User user, final long tripId, final long childTripId) {
        requireOwnedTrip(user, tripId);
        diveTripDataService.removeTripMember(tripId, childTripId);
    }

    public PagedResponse<BasicDiveInfo> getTransitiveDives(
            final User user, final long tripId, final int page, final int pageSize) {
        requireOwnedTrip(user, tripId);
        return diveTripDataService.findTransitiveDives(tripId, page, pageSize);
    }

    public List<DiveTripDefaultTeamMember> getDefaultTeam(final User user, final long tripId) {
        requireOwnedTrip(user, tripId);
        return diveTripDataService.findDefaultTeam(tripId);
    }

    public List<DiveTripDefaultTeamMember> replaceDefaultTeam(
            final User user,
            final long tripId,
            final List<DiveTripDataService.DefaultTeamEntry> entries) {
        requireOwnedTrip(user, tripId);
        return diveTripDataService.replaceDefaultTeam(tripId, entries);
    }

    public List<DiveTrip> getTripsContainingDive(final User user, final long diveId) {
        if (!diveService.hasReadAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        // Trips are single-owner (see class doc) - a shared dive's trip membership must not leak
        // another user's trip names/ids to every reader of that dive, only the trip's own owner.
        return diveTripDataService.findTripsContainingDive(diveId).stream()
                .filter(trip -> trip.ownerUserId() == user.id())
                .toList();
    }
}
