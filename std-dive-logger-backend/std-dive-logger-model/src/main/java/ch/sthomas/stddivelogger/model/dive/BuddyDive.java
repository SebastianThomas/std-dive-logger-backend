package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.user.FrontendUser;

import org.jspecify.annotations.Nullable;

/**
 * {@code role} is this buddy's role as seen from the dive being viewed - not necessarily the same
 * value the buddy's own dive would report back (see {@code DiveBuddyEntity}'s directional role
 * columns). Null on {@code SimplifiedDive} (list views don't resolve per-viewpoint role).
 */
public record BuddyDive(FrontendUser buddy, long diveId, @Nullable BuddyRole role) {}
