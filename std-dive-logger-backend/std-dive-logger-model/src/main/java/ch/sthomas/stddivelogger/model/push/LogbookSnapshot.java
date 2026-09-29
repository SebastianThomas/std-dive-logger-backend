package ch.sthomas.stddivelogger.model.push;

import ch.sthomas.stddivelogger.model.dive.home.HomeActivity;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecentDive;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The headline of the home dashboard, pushed to a device so its offline copy stays current. Field
 * names match {@code HomeDashboard} one-to-one: the frontend overlays it onto the cached dashboard
 * with a plain spread ({@code lib/offline/syncSnapshot.ts}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogbookSnapshot(
        long diveCount,
        int maxDiveNumber,
        @Nullable Duration totalBottomTime,
        @Nullable Double maxDepth,
        @Nullable Instant firstDiveStart,
        @Nullable Instant lastDiveStart,
        long divesThisYear,
        HomeActivity windows,
        List<HomeRecentDive> recentDives) {

    public LogbookSnapshot withRecentDives(final List<HomeRecentDive> dives) {
        return new LogbookSnapshot(
                diveCount,
                maxDiveNumber,
                totalBottomTime,
                maxDepth,
                firstDiveStart,
                lastDiveStart,
                divesThisYear,
                windows,
                dives);
    }
}
