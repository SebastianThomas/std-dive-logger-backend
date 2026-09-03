package ch.sthomas.stddivelogger.model.dive.home;

/**
 * One calendar month's dive count, for the home dashboard's activity-rate logic (the frontend
 * detects real diving pauses from the gaps between these and derives a recent, pause-aware rate).
 * {@code month} is {@code "YYYY-MM"}; only months with at least one dive are present.
 */
public record HomeMonthlyCount(String month, int count) {}
