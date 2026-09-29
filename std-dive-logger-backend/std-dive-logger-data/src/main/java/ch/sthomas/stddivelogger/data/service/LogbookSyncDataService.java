package ch.sthomas.stddivelogger.data.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Change detection for the logbook-sync push. DB-level (the same dive fingerprint as {@link
 * DiverActivityStatsDataService}, grouped), so edits in {@code ws}, commits in {@code import-ws}
 * and re-processing in {@code analytics} are all seen - an in-process event would miss the latter
 * two. Only divers with a sync-enabled device and a live session are ever scanned.
 */
@Service
public class LogbookSyncDataService {

    /** Trailing debounce: a bulk import (dozens of commits) settles into one push. */
    static final String QUIET_PERIOD = "2 minutes";

    /** At most one sync push per diver per this interval (plus the push service's Topic). */
    static final String MIN_INTERVAL = "10 minutes";

    private static final String Q_DUE =
            """
            WITH subscribed AS (
                SELECT DISTINCT s.fk_user_id AS user_id
                FROM t_push_subscription s
                WHERE s.logbook_sync
                  AND EXISTS (SELECT 1 FROM t_refresh_tokens r
                              WHERE r.fk_user_id = s.fk_user_id AND r.expires_at > now())
            ),
            fingerprints AS (
                SELECT u.user_id,
                       count(d.pk_dive_id)::text || '|'
                         || coalesce(sum(extract(epoch FROM ds.dive_start))::bigint, 0)::text || '|'
                         || coalesce(sum(d.pk_dive_id), 0)::text || '|'
                         || coalesce(extract(epoch FROM max(d.updated_at))::bigint, 0)::text || '|'
                         || (count(d.pk_dive_id) FILTER (WHERE d.highlighted))::text AS fingerprint,
                       max(d.updated_at) AS last_change
                FROM subscribed u
                LEFT JOIN (t_dives d JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id)
                       ON d.fk_diver_id = u.user_id
                GROUP BY u.user_id
            )
            SELECT f.user_id, f.fingerprint, st.fk_user_id IS NOT NULL AS known
            FROM fingerprints f
            LEFT JOIN t_logbook_sync_state st ON st.fk_user_id = f.user_id
            WHERE st.fk_user_id IS NULL
               OR (st.fingerprint <> f.fingerprint
                   AND (st.sent_at IS NULL OR st.sent_at < now() - CAST(:minInterval AS interval))
                   AND (f.last_change IS NULL
                        OR f.last_change < now() - CAST(:quietPeriod AS interval)))
            ORDER BY f.user_id
            LIMIT :limit
            """;

    private static final String Q_SEED =
            """
            INSERT INTO t_logbook_sync_state (fk_user_id, fingerprint, sent_at)
            VALUES (:userId, :fingerprint, NULL)
            ON CONFLICT (fk_user_id) DO NOTHING
            """;

    private static final String Q_MARK_SYNCED =
            """
            INSERT INTO t_logbook_sync_state (fk_user_id, fingerprint, sent_at)
            VALUES (:userId, :fingerprint, now())
            ON CONFLICT (fk_user_id) DO UPDATE SET fingerprint = :fingerprint, sent_at = now()
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LogbookSyncDataService(final NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A diver whose logbook changed since the last push (and has been quiet for a moment), or who
     * has never been seen ({@code known == false}: seed only, so a deploy pushes nothing).
     */
    public record Due(long userId, String fingerprint, boolean known) {}

    @Transactional(readOnly = true)
    public List<Due> findDue(final int limit) {
        final var params =
                new MapSqlParameterSource("limit", limit)
                        .addValue("minInterval", MIN_INTERVAL)
                        .addValue("quietPeriod", QUIET_PERIOD);
        return jdbc.query(
                Q_DUE,
                params,
                (rs, _) ->
                        new Due(
                                rs.getLong("user_id"),
                                rs.getString("fingerprint"),
                                rs.getBoolean("known")));
    }

    /** Remembers the current state without pushing (first sighting of a diver). */
    @Transactional
    public void seed(final long userId, final String fingerprint) {
        jdbc.update(
                Q_SEED,
                new MapSqlParameterSource("userId", userId).addValue("fingerprint", fingerprint));
    }

    /** Called after an attempt, delivered or not: a dead endpoint must not re-trigger each run. */
    @Transactional
    public void markSynced(final long userId, final String fingerprint) {
        jdbc.update(
                Q_MARK_SYNCED,
                new MapSqlParameterSource("userId", userId).addValue("fingerprint", fingerprint));
    }
}
