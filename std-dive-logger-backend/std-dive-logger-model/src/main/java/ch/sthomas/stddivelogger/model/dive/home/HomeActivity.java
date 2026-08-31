package ch.sthomas.stddivelogger.model.dive.home;

/**
 * Raw rolling-window dive activity for the home dashboard. The frontend decides which window to
 * headline (this month when recently active, 12 months when steady, all-time otherwise) - the
 * backend just hands over the buckets.
 *
 * <p>{@code last30Days} overlaps {@code last365Days}; {@code previous365Days} is the 12 months
 * before that (disjoint from {@code last365Days}), for a "vs the year before" comparison.
 */
public record HomeActivity(
        HomeWindow last30Days, HomeWindow last365Days, HomeWindow previous365Days) {
    public static final HomeActivity EMPTY =
            new HomeActivity(HomeWindow.EMPTY, HomeWindow.EMPTY, HomeWindow.EMPTY);
}
