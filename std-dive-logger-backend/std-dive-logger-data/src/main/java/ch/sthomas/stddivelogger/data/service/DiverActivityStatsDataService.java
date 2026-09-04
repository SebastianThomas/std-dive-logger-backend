package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DiverActivityStatsRepository;
import ch.sthomas.stddivelogger.model.dive.home.CadenceTrend;
import ch.sthomas.stddivelogger.model.dive.home.DepthTrend;
import ch.sthomas.stddivelogger.model.dive.home.DiverActivityStats;
import ch.sthomas.stddivelogger.model.dive.home.HomeMonthlyCount;
import ch.sthomas.stddivelogger.model.dive.home.NudgeLevel;
import ch.sthomas.stddivelogger.model.entity.DiverActivityStatsEntity;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes and caches {@link DiverActivityStats} - the home dashboard's pause-aware activity rate,
 * streaks, seasonality, depth trend and "time to dive again" nudge. Raw-SQL aggregates (never
 * entity graphs), in the {@link HomeDataService} style. The analytics deployable calls {@link
 * #findDiverIdsNeedingRecompute}/{@link #computeAndStore} on a schedule; {@code ws} reads {@link
 * #getOrCompute}.
 */
@Service
public class DiverActivityStatsDataService {

    /** Same composite as {@link DiverActivityStatsRepository#findDiverIdsNeedingRecompute}. */
    private static final String FINGERPRINT_SQL =
            """
            SELECT count(*)::text || '|'
              || coalesce(sum(extract(epoch FROM ds.dive_start))::bigint, 0)::text || '|'
              || coalesce(sum(d.pk_dive_id), 0)::text || '|'
              || coalesce(extract(epoch FROM max(d.updated_at))::bigint, 0)::text || '|'
              || coalesce(count(*) FILTER (WHERE d.highlighted), 0)::text
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            """;

    private static final String Q_MONTHLY =
            """
            SELECT to_char(date_trunc('month', ds.dive_start), 'YYYY-MM') AS ym, count(*) AS c
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            GROUP BY 1 ORDER BY 1
            """;

    private static final String Q_RECENT_STARTS =
            """
            SELECT ds.dive_start
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            ORDER BY ds.dive_start DESC
            LIMIT 40
            """;

    private static final String Q_SUMMARY =
            """
            SELECT
              count(*)                                                                 AS n,
              max(ds.dive_start)                                                        AS last_start,
              count(*) FILTER (WHERE date_trunc('year', ds.dive_start)
                                   = date_trunc('year', now()))                         AS dives_this_year,
              count(DISTINCT d.dive_site)                                               AS distinct_sites,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '30 days')        AS w30,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '90 days')        AS w90,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '182 days')       AS w182,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '180 days'
                                 AND ds.dive_start <  now() - interval '90 days')        AS w90_prior,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '365 days')       AS recent_n,
              avg(ds.max_depth) FILTER (WHERE ds.dive_start >= now() - interval '365 days') AS recent_avg_depth,
              count(*) FILTER (WHERE ds.dive_start >= now() - interval '730 days'
                                 AND ds.dive_start <  now() - interval '365 days')       AS prior_n,
              avg(ds.max_depth) FILTER (WHERE ds.dive_start >= now() - interval '730 days'
                                          AND ds.dive_start <  now() - interval '365 days') AS prior_avg_depth
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            """;

    private static final String Q_NEW_SITES_THIS_YEAR =
            """
            SELECT count(*) FROM (
              SELECT d.dive_site, min(ds.dive_start) AS first_here
              FROM t_dives d
              JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
              WHERE d.fk_diver_id = :userId
              GROUP BY d.dive_site
            ) x
            WHERE date_trunc('year', x.first_here) = date_trunc('year', now())
            """;

    private static final String Q_SEASONALITY =
            """
            SELECT extract(month FROM ds.dive_start)::int AS m, count(*) AS c
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            GROUP BY 1
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DiverActivityStatsRepository repo;

    public DiverActivityStatsDataService(
            final NamedParameterJdbcTemplate jdbc, final DiverActivityStatsRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Long> findDiverIdsNeedingRecompute(final int limit) {
        return repo.findDiverIdsNeedingRecompute(DiverActivityStats.VERSION, limit);
    }

    /**
     * The cached blob if it's at the current version; empty otherwise (missing or stale version).
     */
    @Transactional(readOnly = true)
    public Optional<DiverActivityStats> findCached(final long userId) {
        return repo.findByDiverId(userId)
                .filter(e -> e.getComputedVersion() == DiverActivityStats.VERSION)
                .map(DiverActivityStatsEntity::getStats);
    }

    /**
     * Recompute from scratch and upsert the cache row. The fingerprint is read *before* the compute
     * so a change landing mid-compute is still picked up on the next sweep.
     */
    @Transactional
    public DiverActivityStats computeAndStore(final long userId) {
        final var fingerprint = fingerprint(userId);
        final var stats = compute(userId);
        repo.findByDiverId(userId)
                .ifPresentOrElse(
                        e ->
                                e.update(
                                        Instant.now(),
                                        DiverActivityStats.VERSION,
                                        fingerprint,
                                        stats),
                        () ->
                                repo.save(
                                        new DiverActivityStatsEntity(
                                                userId,
                                                Instant.now(),
                                                DiverActivityStats.VERSION,
                                                fingerprint,
                                                stats)));
        return stats;
    }

    /**
     * The cached blob, or - on a miss - one computed and stored right now. Writes, so the caller's
     * transaction ({@code HomeDataService.forUser}) must not be read-only.
     */
    @Transactional
    public DiverActivityStats getOrCompute(final long userId) {
        return findCached(userId).orElseGet(() -> computeAndStore(userId));
    }

    private String fingerprint(final long userId) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        FINGERPRINT_SQL,
                        new MapSqlParameterSource("userId", userId),
                        String.class));
    }

    @Transactional(readOnly = true)
    public DiverActivityStats compute(final long userId) {
        final var params = new MapSqlParameterSource("userId", userId);

        final List<HomeMonthlyCount> byMonth =
                jdbc.query(
                        Q_MONTHLY,
                        params,
                        (rs, i) -> new HomeMonthlyCount(rs.getString("ym"), rs.getInt("c")));
        if (byMonth.isEmpty()) {
            return DiverActivityStats.empty();
        }

        final var summary =
                Objects.requireNonNull(
                        jdbc.queryForObject(Q_SUMMARY, params, DiverActivityStatsDataService::sum));
        final List<Instant> recentStartsDesc =
                jdbc.query(
                        Q_RECENT_STARTS,
                        params,
                        (rs, i) -> Objects.requireNonNull(rs.getTimestamp(1)).toInstant());
        final int newSitesThisYear =
                Objects.requireNonNullElse(
                        jdbc.queryForObject(Q_NEW_SITES_THIS_YEAR, params, Integer.class), 0);
        final var seasonality =
                jdbc.query(Q_SEASONALITY, params, DiverActivityStatsDataService::mc);

        final long diveCount = summary.n();
        final var now = Instant.now();

        // --- era + rate ---
        final var era = currentEra(byMonth);
        final double perMonth = era.ratePerMonth();
        final int perYear = (int) Math.round(perMonth * 12);

        // --- cadence + nudge (dynamic, per-diver) ---
        final var recentStartsAsc = new ArrayList<>(recentStartsDesc);
        recentStartsAsc.sort(Comparator.naturalOrder());
        final var allGaps = gapsDays(recentStartsAsc);
        // Gaps whose later dive is within the last ~14 months: "when this diver dives lately, how
        // far apart" - unlike windowDays/count, this ignores the trailing time-since-last-dive,
        // so a diver mid-dry-spell still shows their true rhythm.
        final var recentGaps = recentGaps(recentStartsAsc, now, 430);
        final Integer medianAllGap = median(allGaps);

        final Integer recentCadenceDays =
                !recentGaps.isEmpty() && recentGaps.size() >= 2
                        ? median(recentGaps)
                        : dynamicCadenceDays(summary, medianAllGap);
        final CadenceTrend cadenceTrend = cadenceTrend(summary);

        final Integer typicalIntervalDays =
                recentCadenceDays != null ? recentCadenceDays : medianAllGap;
        // Prefer recent gaps for the regularity read; fall back to all recent history.
        final var regularityGaps = recentGaps.size() >= 3 ? recentGaps : allGaps;

        final Instant lastStart = summary.lastStart();
        final Integer daysSinceLast =
                lastStart == null
                        ? null
                        : (int) Math.max(0, ChronoUnit.DAYS.between(lastStart, now));

        // Threshold = cadence x a regularity-adjusted multiple, nudged out when they're already
        // slowing down / in when they're picking up. Clamped so we never nag a very frequent
        // diver after a few days, nor a very occasional one after a normal-for-them gap.
        Integer nudgeThresholdDays = null;
        NudgeLevel nudgeLevel = NudgeLevel.NONE;
        if (recentCadenceDays != null && daysSinceLast != null) {
            double multiple = regularityMultiple(regularityGaps);
            multiple *=
                    switch (cadenceTrend) {
                        case SLOWING -> 1.3;
                        case PICKING_UP -> 0.85;
                        default -> 1.0;
                    };
            nudgeThresholdDays =
                    (int) Math.max(10, Math.min(550, Math.round(recentCadenceDays * multiple)));
            final double ratio = (double) daysSinceLast / nudgeThresholdDays;
            nudgeLevel =
                    ratio < 1.0
                            ? NudgeLevel.NONE
                            : ratio < 1.6
                                    ? NudgeLevel.GENTLE
                                    : ratio < 4.0 ? NudgeLevel.KEEN : NudgeLevel.DORMANT;
        }
        final boolean overdue = nudgeLevel == NudgeLevel.GENTLE || nudgeLevel == NudgeLevel.KEEN;

        final Instant expectedNextBy =
                (lastStart != null && typicalIntervalDays != null)
                        ? lastStart.plus(Duration.ofDays(typicalIntervalDays))
                        : null;

        // --- streaks ---
        final int[] streaks = monthStreaks(byMonth);

        // --- seasonality ---
        Integer busiestMonth = null;
        double busiestShare = 0;
        final long seasonTotal = seasonality.stream().mapToLong(MonthCount::count).sum();
        final var top =
                seasonality.stream().max(Comparator.comparingLong(MonthCount::count)).orElse(null);
        if (top != null && seasonTotal > 0) {
            busiestMonth = top.month();
            busiestShare = (double) top.count() / seasonTotal;
        }

        // --- depth trend ---
        DepthTrend depthTrend = DepthTrend.UNKNOWN;
        final Double recentAvgDepth = summary.recentAvgDepth();
        final Double priorAvgDepth = summary.priorAvgDepth();
        if (summary.recentN() >= 3
                && summary.priorN() >= 3
                && recentAvgDepth != null
                && priorAvgDepth != null) {
            final double delta = recentAvgDepth - priorAvgDepth;
            depthTrend =
                    delta > 1.5
                            ? DepthTrend.DEEPER
                            : delta < -1.5 ? DepthTrend.SHALLOWER : DepthTrend.STEADY;
        }

        // --- this year / projection / milestone ---
        final int divesThisYear = (int) summary.divesThisYear();
        final double yearFraction = fractionOfYearElapsed(now);
        final Integer projected =
                (yearFraction >= 0.15 && divesThisYear >= 1)
                        ? (int) Math.round(divesThisYear / yearFraction)
                        : null;
        final Integer nextMilestone = nextMilestone(diveCount);
        final Integer toMilestone =
                (nextMilestone != null && nextMilestone - diveCount <= 10)
                        ? (int) (nextMilestone - diveCount)
                        : null;

        return new DiverActivityStats(
                byMonth,
                perMonth,
                perYear,
                era.startMonth(),
                era.precededByPause(),
                typicalIntervalDays,
                daysSinceLast,
                expectedNextBy,
                overdue,
                recentCadenceDays,
                cadenceTrend,
                nudgeThresholdDays,
                nudgeLevel,
                streaks[0],
                streaks[1],
                busiestMonth,
                busiestShare,
                depthTrend,
                recentAvgDepth,
                priorAvgDepth,
                (int) summary.distinctSites(),
                newSitesThisYear,
                divesThisYear,
                projected,
                toMilestone != null ? nextMilestone : null,
                toMilestone);
    }

    // ---------------------------------------------------------------------------------------------
    // Java-side maths (mirrors the frontend's old activityFraming.ts era logic).
    // ---------------------------------------------------------------------------------------------

    private record Era(double ratePerMonth, @Nullable String startMonth, boolean precededByPause) {}

    private record MonthEntry(int idx, String ym, int count) {}

    /**
     * The current diving era: walk back from the most recent dive-month, stopping at the first gap
     * that's a real pause *for this diver* - ~3x their own median month-to-month gap (4-15 months;
     * a generous 5 while the cadence is still unknown).
     */
    static Era currentEra(final List<HomeMonthlyCount> byMonthAsc) {
        final var months =
                byMonthAsc.stream()
                        .filter(m -> m.count() > 0)
                        .map(m -> new MonthEntry(monthIndex(m.month()), m.month(), m.count()))
                        .sorted(Comparator.comparingInt(MonthEntry::idx))
                        .toList();
        if (months.isEmpty()) {
            return new Era(0, null, false);
        }
        int start = months.size() - 1;
        final var eraGaps = new ArrayList<Integer>();
        for (int i = months.size() - 1; i > 0; i--) {
            final int gap = months.get(i).idx() - months.get(i - 1).idx();
            final int cutoff =
                    eraGaps.size() >= 2
                            ? Math.max(4, Math.min(15, (int) Math.round(medianDouble(eraGaps) * 3)))
                            : 5;
            if (gap > cutoff) {
                break;
            }
            eraGaps.add(gap);
            start = i - 1;
        }
        final var era = months.subList(start, months.size());
        final long dives = era.stream().mapToLong(MonthEntry::count).sum();
        final int spanMonths = Math.max(1, era.get(era.size() - 1).idx() - era.get(0).idx() + 1);
        return new Era((double) dives / spanMonths, era.get(0).ym(), start > 0);
    }

    /** [currentStreak, longestStreak] - consecutive calendar months with at least one dive. */
    static int[] monthStreaks(final List<HomeMonthlyCount> byMonthAsc) {
        final var idxs =
                byMonthAsc.stream()
                        .filter(m -> m.count() > 0)
                        .map(m -> monthIndex(m.month()))
                        .sorted()
                        .toList();
        if (idxs.isEmpty()) {
            return new int[] {0, 0};
        }
        int longest = 1;
        int run = 1;
        for (int i = 1; i < idxs.size(); i++) {
            run = idxs.get(i) - idxs.get(i - 1) == 1 ? run + 1 : 1;
            longest = Math.max(longest, run);
        }
        // "current" streak only counts if the last active month is this month or last month.
        final int nowIdx =
                YearMonth.now(ZoneOffset.UTC).getYear() * 12
                        + YearMonth.now(ZoneOffset.UTC).getMonthValue()
                        - 1;
        final int current = (nowIdx - idxs.get(idxs.size() - 1)) <= 1 ? run : 0;
        return new int[] {current, longest};
    }

    /**
     * Gaps (days) between consecutive dives that *both* fall within {@code withinDays} of now - so
     * a long pause just before the recent stretch isn't counted as part of the current rhythm.
     */
    private static List<Integer> recentGaps(
            final List<Instant> ascStarts, final Instant now, final int withinDays) {
        final var gaps = new ArrayList<Integer>();
        for (int i = 1; i < ascStarts.size(); i++) {
            if (ChronoUnit.DAYS.between(ascStarts.get(i - 1), now) <= withinDays) {
                gaps.add(
                        (int)
                                Math.max(
                                        0,
                                        ChronoUnit.DAYS.between(
                                                ascStarts.get(i - 1), ascStarts.get(i))));
            }
        }
        return gaps;
    }

    /** Day gaps between consecutive dives (ascending starts); empty when there are fewer than 2. */
    private static List<Integer> gapsDays(final List<Instant> ascStarts) {
        final var gaps = new ArrayList<Integer>();
        for (int i = 1; i < ascStarts.size(); i++) {
            gaps.add(
                    (int)
                            Math.max(
                                    0,
                                    ChronoUnit.DAYS.between(
                                            ascStarts.get(i - 1), ascStarts.get(i))));
        }
        return gaps;
    }

    /** Median gap in days, or null with fewer than 2 gaps (3 dives). */
    private static @Nullable Integer median(final List<Integer> xs) {
        if (xs.size() < 2) {
            return null;
        }
        final var s = xs.stream().sorted().toList();
        final int m = s.size() / 2;
        final double med = s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2.0;
        return (int) Math.max(1, Math.round(med));
    }

    /**
     * Expected days between dives from the diver's *current* pace: the shortest trailing window (90
     * / 182 / 365 d) that still holds >= 3 dives, else 365/n if they managed >= 2 in a year, else
     * their all-time median gap (a long-dormant diver). null when there's no signal at all.
     */
    private static @Nullable Integer dynamicCadenceDays(
            final SummaryRow s, final @Nullable Integer medianGap) {
        if (s.w90() >= 3) {
            return clampCadence(90.0 / s.w90());
        }
        if (s.w182() >= 3) {
            return clampCadence(182.0 / s.w182());
        }
        if (s.recentN() >= 2) {
            return clampCadence(365.0 / s.recentN());
        }
        return medianGap;
    }

    private static int clampCadence(final double days) {
        return (int) Math.max(1, Math.min(400, Math.round(days)));
    }

    /** Last ~90 days vs the ~90 before. */
    private static CadenceTrend cadenceTrend(final SummaryRow s) {
        final long last = s.w90();
        final long prior = s.w90Prior();
        if (last + prior < 3) {
            return CadenceTrend.UNKNOWN;
        }
        if (last > prior * 1.3) {
            return CadenceTrend.PICKING_UP;
        }
        if (last < prior * 0.7) {
            return CadenceTrend.SLOWING;
        }
        return CadenceTrend.STEADY;
    }

    /**
     * How much slack to give before nudging: a metronomic diver (low gap variance) is "overdue"
     * soon after their usual interval; a sporadic one gets much more room. 1.4x - 3.0x.
     */
    private static double regularityMultiple(final List<Integer> gaps) {
        if (gaps.size() < 3) {
            return 1.8;
        }
        final double mean = gaps.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (mean <= 0) {
            return 1.8;
        }
        final double variance =
                gaps.stream().mapToDouble(g -> (g - mean) * (g - mean)).average().orElse(0);
        final double cv = Math.sqrt(variance) / mean;
        return Math.max(1.4, Math.min(3.0, 1.4 + cv));
    }

    private static double medianDouble(final List<Integer> xs) {
        final var s = xs.stream().sorted().toList();
        final int m = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2.0;
    }

    private static int monthIndex(final String ym) {
        final var parts = ym.split("-");
        return Integer.parseInt(parts[0]) * 12 + (Integer.parseInt(parts[1]) - 1);
    }

    private static double fractionOfYearElapsed(final Instant now) {
        final var date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        final int daysInYear = date.isLeapYear() ? 366 : 365;
        return (double) date.getDayOfYear() / daysInYear;
    }

    private static @Nullable Integer nextMilestone(final long count) {
        final int step = count < 100 ? 25 : count < 500 ? 50 : 100;
        final long next = ((count / step) + 1) * step;
        return (int) next;
    }

    // --- row mappers ---

    private record SummaryRow(
            long n,
            @Nullable Instant lastStart,
            long divesThisYear,
            long distinctSites,
            long w30,
            long w90,
            long w182,
            long w90Prior,
            long recentN,
            @Nullable Double recentAvgDepth,
            long priorN,
            @Nullable Double priorAvgDepth) {}

    private static SummaryRow sum(final ResultSet rs, final int rowNum) throws SQLException {
        return new SummaryRow(
                rs.getLong("n"),
                ts(rs, "last_start"),
                rs.getLong("dives_this_year"),
                rs.getLong("distinct_sites"),
                rs.getLong("w30"),
                rs.getLong("w90"),
                rs.getLong("w182"),
                rs.getLong("w90_prior"),
                rs.getLong("recent_n"),
                dbl(rs, "recent_avg_depth"),
                rs.getLong("prior_n"),
                dbl(rs, "prior_avg_depth"));
    }

    private record MonthCount(int month, long count) {}

    private static MonthCount mc(final ResultSet rs, final int rowNum) throws SQLException {
        return new MonthCount(rs.getInt("m"), rs.getLong("c"));
    }

    private static @Nullable Instant ts(final ResultSet rs, final String col) throws SQLException {
        final var t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static @Nullable Double dbl(final ResultSet rs, final String col) throws SQLException {
        final var v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
