package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Home-dashboard activity & trend stats for one diver. The expensive bits (streaks, seasonality,
 * depth trend, per-month history) are recomputed by the analytics deployable only when the diver's
 * dives changed, and cached in {@code t_diver_activity_stats}; {@code ws} reads the cached blob
 * straight through. Serialized whole into a {@code jsonb} column, so a new field is a {@link
 * #VERSION} bump, not a migration.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiverActivityStats(
        // --- rate over the current diving "era" (real pauses before it excluded) ---
        List<HomeMonthlyCount> divesByMonth,
        double recentDivesPerMonth,
        int recentDivesPerYear,
        // "YYYY-MM" the current era started at; null on an empty logbook.
        @Nullable String eraStartMonth,
        // true when there are earlier dives cut off from the era by a real pause.
        boolean eraPrecededByPause,

        // --- cadence + "time to go diving again" nudge ---
        // Typical days between consecutive dives: the dynamic recentCadenceDays when we have one,
        // otherwise the median gap over recent history.
        @Nullable Integer typicalIntervalDays,
        @Nullable Integer daysSinceLastDive,
        // lastDive + typicalIntervalDays.
        @Nullable Instant expectedNextDiveBy,
        // true once the nudge is GENTLE or KEEN (kept for the frontend's simple checks).
        boolean overdue,
        // Dynamic expected interval: derived from the shortest recent trailing window
        // (30/90/182/365 d) that still has enough dives to be meaningful. Adapts to the diver's
        // *current* pace, not their all-time median. null until there's any recent history.
        @Nullable Integer recentCadenceDays,
        // last ~90 days vs the ~90 before - sharpens (PICKING_UP/STEADY) or softens (SLOWING) the
        // nudge.
        CadenceTrend cadenceTrend,
        // daysSinceLastDive at which the nudge starts firing for this diver (cadence x a
        // regularity-adjusted multiple, clamped). null when we can't estimate a cadence.
        @Nullable Integer nudgeThresholdDays,
        NudgeLevel nudgeLevel,

        // --- streaks: consecutive calendar months with at least one dive ---
        int currentMonthStreak,
        int longestMonthStreak,

        // --- seasonality: which month of the year the diver dives most ---
        @Nullable Integer busiestMonth, // 1-12
        double busiestMonthShare, // fraction of all this diver's dives that fall in busiestMonth

        // --- depth trend: last 12 months vs the 12 before ---
        DepthTrend depthTrend,
        @Nullable Double recentAvgMaxDepth,
        @Nullable Double priorAvgMaxDepth,

        // --- site exploration ---
        int distinctSites,
        int newSitesThisYear,

        // --- this year / milestones ---
        int divesThisYear,
        // this year's count extrapolated to year-end at the current pace; null early in the year.
        @Nullable Integer projectedDivesThisYear,
        // the next round number of dives (25/50/100/...), and how many to go.
        @Nullable Integer nextMilestone,
        @Nullable Integer divesToNextMilestone) {

    /**
     * Bump when the computation changes, to re-run it for every diver on the next analytics sweep.
     * v2: dynamic per-diver cadence + nudge level ({@link #recentCadenceDays}, {@link
     * #cadenceTrend}, {@link #nudgeThresholdDays}, {@link #nudgeLevel}).
     */
    public static final int VERSION = 2;

    public static DiverActivityStats empty() {
        return new DiverActivityStats(
                List.of(),
                0,
                0,
                null,
                false,
                null,
                null,
                null,
                false,
                null,
                CadenceTrend.UNKNOWN,
                null,
                NudgeLevel.NONE,
                0,
                0,
                null,
                0,
                DepthTrend.UNKNOWN,
                null,
                null,
                0,
                0,
                0,
                null,
                null,
                null);
    }
}
