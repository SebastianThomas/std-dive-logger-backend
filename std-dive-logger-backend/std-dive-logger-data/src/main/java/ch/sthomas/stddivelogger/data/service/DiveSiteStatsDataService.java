package ch.sthomas.stddivelogger.data.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final NamedParameterJdbcTemplate jdbc;

    public DiveSiteStatsDataService(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int refreshAll() {
        return jdbc.update(REFRESH_SQL, new MapSqlParameterSource());
    }
}
