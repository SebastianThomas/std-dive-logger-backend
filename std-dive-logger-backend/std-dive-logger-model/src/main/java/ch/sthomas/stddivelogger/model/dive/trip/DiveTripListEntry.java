package ch.sthomas.stddivelogger.model.dive.trip;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One row of the trip list - {@link DiveTrip} itself plus the earliest/latest start date across
 * every dive transitively under it (its own direct dives and every nested sub-trip's), used to
 * order the list by actual diving activity rather than by database id/creation order (see {@code
 * DiveTripDataService.findTripsByOwnerWithDateRange}). Both null for a trip with no dives under it
 * yet.
 */
public record DiveTripListEntry(
        DiveTrip trip, @Nullable Instant firstDiveDate, @Nullable Instant lastDiveDate) {}
