package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Everything the logged-in home page shows, in one payload from {@code GET /v1/home}. Most of it is
 * aggregated live from {@code t_dive_summary} (+ small joins) on every request - a handful of
 * indexed queries, no entity graphs, no {@code t_dive_measurements}. The heavier
 * activity/trend/streak maths ({@link DiverActivityStats}) is precomputed by the analytics
 * deployable and read from a cache row.
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
        // Pause-aware activity rate, streaks, seasonality, depth trend, "time to dive again"
        // nudge - recomputed by the analytics deployable only when the diver's dives change,
        // cached in t_diver_activity_stats. See DiverActivityStats.
        DiverActivityStats activityStats,
        // Time-sensitive prompts: dive anniversaries ("3 years ago today ...") and the dynamic
        // "time to go diving again" nudge. Computed + stored by the analytics deployable,
        // recomputed daily; only the currently-relevant, not-dismissed ones are here. Empty list
        // when there are none.
        List<DiverReminder> reminders,
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
                DiverActivityStats.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                HomeRecords.NONE);
    }
}
