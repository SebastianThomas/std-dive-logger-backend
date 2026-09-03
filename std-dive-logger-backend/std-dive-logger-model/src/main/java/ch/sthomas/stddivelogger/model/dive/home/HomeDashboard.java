package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Everything the logged-in home page shows, in one payload from {@code GET /v1/home}. Aggregated
 * live from {@code t_dive_summary} (+ small joins) on every request - four indexed queries, no
 * entity graphs, no {@code t_dive_measurements} - since the home page is hit on nearly every load.
 *
 * <p>{@code userName} is included so the dashboard needs no separate {@code GET /v1/users/} call.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeDashboard(
        String userName,
        long diveCount,
        int maxDiveNumber,
        @Nullable Duration totalBottomTime,
        // Deepest dive's depth (metres); null when the user has no dives yet.
        @Nullable Double maxDepth,
        @Nullable Instant firstDiveStart,
        @Nullable Instant lastDiveStart,
        long divesThisYear,
        HomeActivity windows,
        // Dives per calendar month, ascending, months-with-dives only - the frontend derives a
        // pause-aware "recent rate" from the gaps here rather than an all-time average.
        List<HomeMonthlyCount> divesByMonth,
        List<HomeRecentDive> recentDives,
        // The user's highlighted ('starred') dives, most recent first (capped).
        List<HomeRecentDive> highlightedDives,
        List<HomeBuddy> topBuddies,
        HomeRecords records) {

    public static HomeDashboard empty(final String userName) {
        return new HomeDashboard(
                userName,
                0,
                0,
                null,
                null,
                null,
                null,
                0,
                HomeActivity.EMPTY,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                HomeRecords.NONE);
    }
}
