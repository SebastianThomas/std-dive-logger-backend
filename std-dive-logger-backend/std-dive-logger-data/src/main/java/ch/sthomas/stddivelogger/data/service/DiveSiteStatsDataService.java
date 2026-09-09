package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.model.dive.DiveSiteStats;
import ch.sthomas.stddivelogger.model.dive.DiveSiteStatsPeriod;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Global, anonymous per-site aggregates ({@code t_dive_site_stats}) backing "suggest a dive site".
 * One bulk refresh, not per-diver - cheap enough to just recompute every site each run.
 */
@Service
public class DiveSiteStatsDataService {

    private static final String REFRESH_SQL =
            """
            INSERT INTO t_dive_site_stats (
                fk_dive_site_id, computed_at, total_dives, distinct_divers,
                recent_dives_30d, recent_distinct_divers_30d,
                avg_visibility_m, visibility_sample_size,
                avg_max_depth, min_max_depth, max_max_depth, highlighted_dives
            )
            SELECT
                d.dive_site,
                now(),
                count(*),
                count(DISTINCT d.fk_diver_id),
                count(*) FILTER (WHERE ds.dive_start >= now() - interval '30 days'),
                count(DISTINCT d.fk_diver_id) FILTER (WHERE ds.dive_start >= now() - interval '30 days'),
                avg(v.visibility_meters),
                count(v.visibility_meters),
                avg(ds.max_depth),
                min(ds.max_depth),
                max(ds.max_depth),
                count(*) FILTER (WHERE d.highlighted)
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            LEFT JOIN t_dive_visibility v ON v.fk_dive_id = d.pk_dive_id
            GROUP BY d.dive_site
            ON CONFLICT (fk_dive_site_id) DO UPDATE SET
                computed_at = EXCLUDED.computed_at,
                total_dives = EXCLUDED.total_dives,
                distinct_divers = EXCLUDED.distinct_divers,
                recent_dives_30d = EXCLUDED.recent_dives_30d,
                recent_distinct_divers_30d = EXCLUDED.recent_distinct_divers_30d,
                avg_visibility_m = EXCLUDED.avg_visibility_m,
                visibility_sample_size = EXCLUDED.visibility_sample_size,
                avg_max_depth = EXCLUDED.avg_max_depth,
                min_max_depth = EXCLUDED.min_max_depth,
                max_max_depth = EXCLUDED.max_max_depth,
                highlighted_dives = EXCLUDED.highlighted_dives
            """;

    private static final String SITE_STATS_SQL =
            """
            SELECT computed_at, total_dives, distinct_divers,
                   recent_dives_30d, recent_distinct_divers_30d,
                   avg_visibility_m, visibility_sample_size,
                   avg_max_depth, min_max_depth, max_max_depth, highlighted_dives
            FROM t_dive_site_stats
            WHERE fk_dive_site_id = :siteId
            """;

    private static final String MONTHLY_ACTIVITY_SQL =
            """
            SELECT date_trunc('month', ds.dive_start, 'UTC') AS period_start,
                   count(*) AS dive_count,
                   count(DISTINCT d.fk_diver_id) AS distinct_divers,
                   avg(ds.max_depth) AS avg_max_depth,
                   max(ds.max_depth) AS max_max_depth
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.dive_site = :siteId
            GROUP BY period_start
            ORDER BY period_start
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DiveSiteStatsDataService(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int refreshAll() {
        return jdbc.update(REFRESH_SQL, new MapSqlParameterSource());
    }

    /**
     * Read the same cached site-wide aggregates used by suggestions, with a lightweight monthly
     * series added for the detail page. No diver identities or individual dives are exposed.
     */
    @Transactional(readOnly = true)
    public DiveSiteStats findForSite(final long siteId) {
        final var params = new MapSqlParameterSource("siteId", siteId);
        final List<DiveSiteStatsPeriod> monthlyActivity =
                jdbc.query(MONTHLY_ACTIVITY_SQL, params, DiveSiteStatsDataService::mapPeriod);
        return jdbc.query(SITE_STATS_SQL, params, (rs, _) -> mapStats(rs, monthlyActivity)).stream()
                .findFirst()
                .orElseGet(() -> emptyStats(monthlyActivity));
    }

    private static DiveSiteStats mapStats(
            final ResultSet rs, final List<DiveSiteStatsPeriod> monthlyActivity)
            throws SQLException {
        return new DiveSiteStats(
                rs.getTimestamp("computed_at").toInstant(),
                rs.getLong("total_dives"),
                rs.getLong("distinct_divers"),
                rs.getLong("recent_dives_30d"),
                rs.getLong("recent_distinct_divers_30d"),
                nullableDouble(rs, "avg_visibility_m"),
                rs.getLong("visibility_sample_size"),
                nullableDouble(rs, "avg_max_depth"),
                nullableDouble(rs, "min_max_depth"),
                nullableDouble(rs, "max_max_depth"),
                rs.getLong("highlighted_dives"),
                monthlyActivity);
    }

    private static DiveSiteStatsPeriod mapPeriod(final ResultSet rs, final int rowNum)
            throws SQLException {
        return new DiveSiteStatsPeriod(
                rs.getTimestamp("period_start").toInstant(),
                rs.getLong("dive_count"),
                rs.getLong("distinct_divers"),
                nullableDouble(rs, "avg_max_depth"),
                nullableDouble(rs, "max_max_depth"));
    }

    private static @Nullable Double nullableDouble(final ResultSet rs, final String column)
            throws SQLException {
        final double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static DiveSiteStats emptyStats(final List<DiveSiteStatsPeriod> monthlyActivity) {
        return new DiveSiteStats(null, 0, 0, 0, 0, null, 0, null, null, null, 0, monthlyActivity);
    }
}
