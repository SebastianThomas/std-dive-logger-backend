package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AnalyticsJobStateEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsJobStateRepository extends JpaRepository<AnalyticsJobStateEntity, Long> {

    Optional<AnalyticsJobStateEntity> findByDive_IdAndModuleAndJobName(
            long diveId, String module, String jobName);

    /**
     * Clears every recorded job-state row for a dive, so the next scheduled sweep treats it as
     * needing recompute again - see {@link #findDiveIdsNeedingRecompute} above.
     */
    void deleteByDive_Id(long diveId);

    @Query(
            """
            SELECT d.id FROM DiveEntity d
            WHERE NOT EXISTS (
                SELECT 1 FROM AnalyticsJobStateEntity s
                WHERE s.dive.id = d.id AND s.module = :module AND s.jobName = :jobName
                    AND s.version >= :version
            )
            ORDER BY d.id ASC
            """)
    List<Long> findDiveIdsNeedingRecompute(
            String module, String jobName, long version, Pageable pageable);
}
