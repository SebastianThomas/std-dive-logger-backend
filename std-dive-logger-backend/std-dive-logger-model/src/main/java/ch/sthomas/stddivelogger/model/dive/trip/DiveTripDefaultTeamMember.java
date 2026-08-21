package ch.sthomas.stddivelogger.model.dive.trip;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.user.FrontendUser;

import org.jspecify.annotations.Nullable;

/**
 * One entry of a trip's default team roster (see {@link DiveTrip}'s doc) - either a real linked
 * user ({@code buddyUser} set) or a free-text name ({@code buddyName} set), never both.
 */
public record DiveTripDefaultTeamMember(
        long id, @Nullable FrontendUser buddyUser, @Nullable String buddyName, BuddyRole role) {}
