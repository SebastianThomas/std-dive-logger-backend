package ch.sthomas.stddivelogger.model.dive.trip;

import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;

import org.jspecify.annotations.Nullable;

/**
 * One direct member of a trip - exactly one of {@code dive}/{@code subTrip} is set, matching {@code
 * t_dive_trip_member}'s own exactly-one-of constraint.
 */
public record DiveTripMember(
        MemberType type, @Nullable BasicDiveInfo dive, @Nullable DiveTrip subTrip) {
    public enum MemberType {
        DIVE,
        TRIP
    }
}
