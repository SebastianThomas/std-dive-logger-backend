package ch.sthomas.stddivelogger.model.dive.stats;

import java.util.List;

/**
 * How often each {@code BuddyRole} was assigned to a buddy (named or linked) across the user's own
 * dives, broken down every requested way. Each "assignment" is one buddy-on-one-dive with a
 * non-null role - a dive with three roled buddies contributes three assignments, one dive with no
 * roled buddies contributes none.
 */
public record BuddyRoleStats(
        List<BuddyRoleCount> overall,
        List<BuddyRoleBreakdown> byBuddy,
        List<BuddyRoleBreakdown> bySite,
        List<BuddyRoleBreakdown> byYear,
        List<BuddyRoleBreakdown> byMonth) {}
