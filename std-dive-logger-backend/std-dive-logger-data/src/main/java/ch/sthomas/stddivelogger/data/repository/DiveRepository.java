package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveRepository extends JpaRepository<DiveEntity, Long> {
    List<DiveEntity> findByUser_Id(Long userId);

    @Query("SELECT d FROM DiveEntity d JOIN DiveProfileEntity p WHERE p.id IN :profileIds")
    List<DiveEntity> findByProfileIds(List<Long> profileIds);

    @Modifying
    @Query(
            value =
                    "UPDATE t_dive_profiles SET fk_dive_id = :targetDiveId WHERE pk_dive_profile_id IN (:profileIds)",
            nativeQuery = true)
    void setDiveIdWhereProfileIdIn(
            @Param("targetDiveId") Long targetDiveId, @Param("profileIds") List<Long> profileIds);
}
