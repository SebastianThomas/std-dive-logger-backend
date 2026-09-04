package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiverActivityStatsEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiverActivityStatsRepository
        extends JpaRepository<DiverActivityStatsEntity, Long> {

    Optional<DiverActivityStatsEntity> findByDiverId(long diverId);

    /**
     * Divers whose cached {@code t_diver_activity_stats} row is missing, at an older {@code
     * computed_version}, or no longer matches their dives ({@code source_fingerprint}). The
     * fingerprint is a cheap composite of dive count / start-times / dive-ids / last-edit /
     * highlight count - identical to what {@code DiverActivityStatsDataService} stores. Never
     * computed first.
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
                    LEFT JOIN t_diver_activity_stats s ON s.fk_diver_id = fp.uid
                    WHERE s.fk_diver_id IS NULL
                       OR s.computed_version <> :version
                       OR s.source_fingerprint <> fp.fingerprint
                    ORDER BY s.computed_at ASC NULLS FIRST
                    LIMIT :limit
                    """)
    List<Long> findDiverIdsNeedingRecompute(int version, int limit);
}
