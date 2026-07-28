package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveProfileSegmentEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiveProfileSegmentRepository
        extends JpaRepository<DiveProfileSegmentEntity, Long> {

    @Query(
            value =
                    """
                            SELECT s.*
                            FROM v_readers
                            INNER JOIN t_dives ON dive_id = :diveId AND pk_user_id = :userId AND pk_dive_id = dive_id
                            INNER JOIN t_dive_profiles ON fk_dive_id = pk_dive_id
                            INNER JOIN t_dive_profile_segments s ON pk_dive_profile_id = fk_dive_profile_id
                            """,
            nativeQuery = true)
    List<DiveProfileSegmentEntity> findByReaderAndDiveId(long userId, long diveId);
}
