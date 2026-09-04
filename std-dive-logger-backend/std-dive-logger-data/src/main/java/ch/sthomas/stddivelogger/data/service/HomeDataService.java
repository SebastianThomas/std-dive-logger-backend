package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.model.dive.home.HomeActivity;
import ch.sthomas.stddivelogger.model.dive.home.HomeBuddy;
import ch.sthomas.stddivelogger.model.dive.home.HomeDashboard;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecentDive;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecordDive;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecords;
import ch.sthomas.stddivelogger.model.dive.home.HomeWindow;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The home dashboard's aggregation, in the raw-SQL / {@link NamedParameterJdbcTemplate} style of
 * {@link StatsDataService} (never entity graphs). A handful of small diver-scoped queries against
 * {@code t_dive_summary} (+ {@code t_dive_site} / {@code t_dive_buddy_name}); no {@code
 * t_dive_measurements}, no per-dive record hydration. Runs on nearly every page load - {@code
 * idx_dives_diver_id} (V0_4_11) keeps each one an index scan.
 */
@Service
public class HomeDataService {

    private final NamedParameterJdbcTemplate jdbc;
    private final DiverActivityStatsDataService activityStats;
    private final DiverReminderDataService reminders;

    public HomeDataService(
            final NamedParameterJdbcTemplate jdbc,
            final DiverActivityStatsDataService activityStats,
            final DiverReminderDataService reminders) {
        this.jdbc = jdbc;
        this.activityStats = activityStats;
        this.reminders = reminders;
    }

    private static final String Q_SUMMARY =
            """
            SELECT
              COUNT(*)                                                              AS dive_count,
              COALESCE(MAX(d.dive_number), 0)                                        AS max_dive_number,
              SUM(ds.duration_seconds)                                               AS total_bottom_seconds,
              MAX(ds.max_depth)                                                      AS max_depth,
              MIN(ds.dive_start)                                                     AS first_dive_start,
              MAX(ds.dive_start)                                                     AS last_dive_start,
              COUNT(*) FILTER (
                  WHERE date_trunc('year', ds.dive_start) = date_trunc('year', now()))    AS dives_this_year,
              COUNT(*) FILTER (WHERE ds.dive_start >= now() - interval '30 days')          AS w30_count,
              SUM(ds.duration_seconds) FILTER (WHERE ds.dive_start >= now() - interval '30 days')  AS w30_seconds,
              COUNT(*) FILTER (WHERE ds.dive_start >= now() - interval '365 days')         AS w365_count,
              SUM(ds.duration_seconds) FILTER (WHERE ds.dive_start >= now() - interval '365 days') AS w365_seconds,
              COUNT(*) FILTER (
                  WHERE ds.dive_start >= now() - interval '730 days'
                    AND ds.dive_start <  now() - interval '365 days')                     AS wprev_count,
              SUM(ds.duration_seconds) FILTER (
                  WHERE ds.dive_start >= now() - interval '730 days'
                    AND ds.dive_start <  now() - interval '365 days')                     AS wprev_seconds
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            """;

    private static final String Q_RECENT =
            """
            SELECT d.pk_dive_id AS id, d.dive_number AS number, d.dive_identifier AS identifier,
                   s.name AS site_name, ds.dive_start AS dive_start,
                   ds.max_depth AS max_depth, ds.duration_seconds AS bottom_seconds
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            JOIN t_dive_site s     ON s.pk_dive_site_id = d.dive_site
            WHERE d.fk_diver_id = :userId
            ORDER BY ds.dive_start DESC, d.dive_number DESC
            LIMIT 5
            """;

    // dive_number DESC as a deterministic tie-break so the pick is stable when two dives share the
    // record value (keeps the isolation test from flaking).
    private static final String Q_RECORDS =
            """
            (SELECT 'DEEPEST' AS kind, d.pk_dive_id AS dive_id, d.dive_number AS dive_number,
                    d.dive_identifier AS identifier, ds.dive_start AS dive_start,
                    ds.max_depth AS max_depth, ds.duration_seconds AS bottom_seconds
             FROM t_dives d JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
             WHERE d.fk_diver_id = :userId
             ORDER BY ds.max_depth DESC, d.dive_number DESC
             LIMIT 1)
            UNION ALL
            (SELECT 'LONGEST', d.pk_dive_id, d.dive_number,
                    d.dive_identifier, ds.dive_start,
                    ds.max_depth, ds.duration_seconds
             FROM t_dives d JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
             WHERE d.fk_diver_id = :userId
             ORDER BY ds.duration_seconds DESC, d.dive_number DESC
             LIMIT 1)
            """;

    // Same projection as Q_RECENT (reuses mapRecentDive); the partial idx_dives_highlighted keeps
    // it to the highlighted subset only.
    private static final String Q_HIGHLIGHTED =
            """
            SELECT d.pk_dive_id AS id, d.dive_number AS number, d.dive_identifier AS identifier,
                   s.name AS site_name, ds.dive_start AS dive_start,
                   ds.max_depth AS max_depth, ds.duration_seconds AS bottom_seconds
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            JOIN t_dive_site s     ON s.pk_dive_site_id = d.dive_site
            WHERE d.fk_diver_id = :userId AND d.highlighted
            ORDER BY ds.dive_start DESC, d.dive_number DESC
            LIMIT 6
            """;

    private static final String Q_BUDDIES =
            """
            SELECT b.name AS name, COUNT(*) AS dive_count
            FROM t_dive_buddy_name b
            JOIN t_dives d ON d.pk_dive_id = b.fk_dive_id
            WHERE d.fk_diver_id = :userId
            GROUP BY b.name
            ORDER BY COUNT(*) DESC, b.name
            LIMIT 5
            """;

    // Not read-only: on a cache miss this lazily computes + stores the DiverActivityStats row.
    @Transactional
    public HomeDashboard forUser(final long userId, final String userName) {
        final var params = new MapSqlParameterSource("userId", userId);

        final var summary =
                Objects.requireNonNull(
                        jdbc.queryForObject(Q_SUMMARY, params, HomeDataService::mapSummary));
        final var recentDives = jdbc.query(Q_RECENT, params, HomeDataService::mapRecentDive);
        final var highlightedDives =
                jdbc.query(Q_HIGHLIGHTED, params, HomeDataService::mapRecentDive);
        final var recordRows = jdbc.query(Q_RECORDS, params, HomeDataService::mapRecordRow);
        final var topBuddies = jdbc.query(Q_BUDDIES, params, HomeDataService::mapBuddy);
        // Cached blob (see DiverActivityStatsDataService); computed + stored once here on a miss.
        final var stats = activityStats.getOrCompute(userId);
        // Anniversaries + the "dive again" nudge - recomputed here if the analytics job hasn't
        // caught today yet.
        final var reminderList = reminders.getActiveReminders(userId);

        return new HomeDashboard(
                userName,
                summary.diveCount(),
                summary.maxDiveNumber(),
                summary.totalBottomSeconds() == null
                        ? null
                        : Duration.ofSeconds(summary.totalBottomSeconds()),
                summary.maxDepth(),
                summary.firstDiveStart(),
                summary.lastDiveStart(),
                summary.divesThisYear(),
                new HomeActivity(
                        summary.window30(), summary.window365(), summary.windowPrevious365()),
                stats,
                reminderList,
                recentDives,
                highlightedDives,
                topBuddies,
                new HomeRecords(recordOf(recordRows, "DEEPEST"), recordOf(recordRows, "LONGEST")));
    }

    private static @Nullable HomeRecordDive recordOf(
            final List<RecordRow> rows, final String kind) {
        return rows.stream()
                .filter(r -> r.kind().equals(kind))
                .map(RecordRow::dive)
                .findFirst()
                .orElse(null);
    }

    // --- row mappers ---

    private static SummaryRow mapSummary(final ResultSet rs, final int rowNum) throws SQLException {
        return new SummaryRow(
                rs.getLong("dive_count"),
                rs.getInt("max_dive_number"),
                nullableLong(rs, "total_bottom_seconds"),
                nullableDouble(rs, "max_depth"),
                nullableInstant(rs, "first_dive_start"),
                nullableInstant(rs, "last_dive_start"),
                rs.getLong("dives_this_year"),
                window(rs, "w30_count", "w30_seconds"),
                window(rs, "w365_count", "w365_seconds"),
                window(rs, "wprev_count", "wprev_seconds"));
    }

    private static HomeWindow window(
            final ResultSet rs, final String countCol, final String secondsCol)
            throws SQLException {
        final var count = rs.getLong(countCol);
        final var seconds = nullableLong(rs, secondsCol);
        return new HomeWindow(count, seconds == null ? null : Duration.ofSeconds(seconds));
    }

    private static HomeRecentDive mapRecentDive(final ResultSet rs, final int rowNum)
            throws SQLException {
        final var bottom = nullableLong(rs, "bottom_seconds");
        return new HomeRecentDive(
                rs.getLong("id"),
                rs.getInt("number"),
                rs.getString("identifier"),
                rs.getString("site_name"),
                nullableInstant(rs, "dive_start"),
                nullableDouble(rs, "max_depth"),
                bottom == null ? null : Duration.ofSeconds(bottom));
    }

    private static RecordRow mapRecordRow(final ResultSet rs, final int rowNum)
            throws SQLException {
        final var bottom = nullableLong(rs, "bottom_seconds");
        return new RecordRow(
                rs.getString("kind"),
                new HomeRecordDive(
                        rs.getLong("dive_id"),
                        rs.getInt("dive_number"),
                        rs.getString("identifier"),
                        nullableInstant(rs, "dive_start"),
                        nullableDouble(rs, "max_depth"),
                        bottom == null ? null : Duration.ofSeconds(bottom)));
    }

    private static HomeBuddy mapBuddy(final ResultSet rs, final int rowNum) throws SQLException {
        return new HomeBuddy(rs.getString("name"), rs.getLong("dive_count"));
    }

    // --- SQL-NULL helpers (same shape as StatsDataService's private ones) ---

    private static @Nullable Long nullableLong(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static @Nullable Double nullableDouble(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static @Nullable Instant nullableInstant(final ResultSet rs, final String column)
            throws SQLException {
        final Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private record SummaryRow(
            long diveCount,
            int maxDiveNumber,
            @Nullable Long totalBottomSeconds,
            @Nullable Double maxDepth,
            @Nullable Instant firstDiveStart,
            @Nullable Instant lastDiveStart,
            long divesThisYear,
            HomeWindow window30,
            HomeWindow window365,
            HomeWindow windowPrevious365) {}

    private record RecordRow(String kind, HomeRecordDive dive) {}
}
