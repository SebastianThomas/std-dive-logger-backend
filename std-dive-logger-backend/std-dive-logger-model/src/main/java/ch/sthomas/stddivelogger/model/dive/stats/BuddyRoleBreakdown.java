package ch.sthomas.stddivelogger.model.dive.stats;

import java.util.List;

/**
 * {@code group} is the label for this bucket - a buddy name, a site name, a year ("2026"), or a
 * year-month ("2026-06"), depending on which breakdown list it appears in.
 */
public record BuddyRoleBreakdown(String group, List<BuddyRoleCount> counts, long total) {}
