package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A named buddy, how many of the user's dives they appear on, and when they last dived together -
 * ranked by a recency-weighted score (see {@code HomeDataService.Q_BUDDIES}), not the raw count.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeBuddy(String name, long diveCount, @Nullable Instant lastDivedAt) {}
