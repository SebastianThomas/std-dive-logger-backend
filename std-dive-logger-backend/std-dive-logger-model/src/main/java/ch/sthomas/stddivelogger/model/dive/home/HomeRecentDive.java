package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * One row in the home dashboard's "recent dives" list - a deliberately thin projection of {@code
 * t_dives} + {@code t_dive_summary} + the site name, with no tags / buddies / profiles / preview
 * image (unlike the heavier {@code SimplifiedDive} the dive list uses).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeRecentDive(
        long id,
        int number,
        @Nullable String identifier,
        @Nullable String siteName,
        @Nullable Instant start,
        @Nullable Double maxDepth,
        @Nullable Duration bottomTime) {}
