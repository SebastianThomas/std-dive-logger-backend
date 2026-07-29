package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.AnalyticsDepthVarianceEntity;
import ch.sthomas.stddivelogger.model.entity.embedded.AnalyticsDepthVarianceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyticsDepthVarianceRepository
        extends JpaRepository<AnalyticsDepthVarianceEntity, AnalyticsDepthVarianceId> {
    @Query(
            value =
                    """
                            SELECT a.*
                            FROM v_readers
                            INNER JOIN t_dives ON dive_id = :diveId AND pk_user_id = :userId AND pk_dive_id = dive_id
                            INNER JOIN t_dive_profiles ON fk_dive_id = pk_dive_id
                            INNER JOIN t_dive_profile_segments ON pk_dive_profile_id = fk_dive_profile_id
                            INNER JOIN t_analytics_depth_variance a ON pk_dive_profile_segment_id = fk_profile_segment_id
                            """,
            nativeQuery = true)
    List<AnalyticsDepthVarianceEntity> findByReaderAndDiveId(long userId, long diveId);
}
