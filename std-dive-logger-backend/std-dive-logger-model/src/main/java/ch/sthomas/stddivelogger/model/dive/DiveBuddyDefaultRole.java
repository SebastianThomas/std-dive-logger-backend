package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.user.FrontendUser;

import org.jspecify.annotations.Nullable;

/**
 * A diver's saved default role for one buddy - either a real linked user ({@code buddyUser} set) or
 * a free-text name ({@code buddyName} set), never both. Applied automatically the first time this
 * buddy is added to a dive; see {@code DiveDataService#applyDefaultBuddyRoles}.
 */
public record DiveBuddyDefaultRole(
        long id, @Nullable FrontendUser buddyUser, @Nullable String buddyName, BuddyRole role) {}
