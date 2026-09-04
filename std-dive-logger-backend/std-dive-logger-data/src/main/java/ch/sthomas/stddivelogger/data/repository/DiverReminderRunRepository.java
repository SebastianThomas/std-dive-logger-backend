package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiverReminderRunEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiverReminderRunRepository extends JpaRepository<DiverReminderRunEntity, Long> {

    Optional<DiverReminderRunEntity> findByDiverId(long diverId);

    /**
     * Divers whose reminders need recomputing: no run row yet, the run was on an earlier day
     * (anniversaries move at midnight), or their dives changed since ({@code source_fingerprint} -
     * the same composite {@code t_diver_activity_stats} uses). Oldest run first.
     */
    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT fp.uid
                    FROM (
                        SELECT d.fk_diver_id AS uid,
                          count(*)::text || '|'
                          || coalesce(sum(extract(epoch FROM ds.dive_start))::bigint, 0)::text || '|'
                          || coalesce(sum(d.pk_dive_id), 0)::text || '|'
                          || coalesce(extract(epoch FROM max(d.updated_at))::bigint, 0)::text || '|'
                          || coalesce(count(*) FILTER (WHERE d.highlighted), 0)::text AS fingerprint
                        FROM t_dives d
                        JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                        GROUP BY d.fk_diver_id
                    ) fp
                    LEFT JOIN t_diver_reminder_run r ON r.fk_diver_id = fp.uid
                    WHERE r.fk_diver_id IS NULL
                       OR r.computed_on <> current_date
                       OR r.source_fingerprint <> fp.fingerprint
                    ORDER BY r.computed_at ASC NULLS FIRST
                    LIMIT :limit
                    """)
    List<Long> findDiverIdsNeedingRecompute(int limit);
}
