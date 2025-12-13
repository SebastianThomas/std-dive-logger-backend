package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveRepository extends JpaRepository<DiveEntity, Long> {
    @Query(
            "SELECT d FROM DiveEntity d JOIN DiveProfileEntity dp ON dp.dive = d AND dp.computer.id = :computerId")
    Page<DiveEntity> findByUser_IdAndComputer(Long userId, Long computerId, Pageable pageable);

    Page<DiveEntity> findByUser_IdOrderByNumberDesc(Long userId, Pageable pageable);

    @Query(
            "SELECT d FROM DiveEntity d JOIN DiveProfileEntity p ON p.dive = d WHERE p.id IN :profileIds")
    List<DiveEntity> findByProfileIds(List<Long> profileIds);

    @Query(
            value =
                    "SELECT d.* FROM t_dives d INNER JOIN t_readers r ON r.pk_user_id = :userId AND r.dive_id = d.pk_dive_id AND d.dive_identifier % :name ORDER BY similarity(d.dive_identifier, :identifier) DESC, LENGTH(d.dive_identifier) ASC",
            countQuery =
                    "SELECT COUNT(*) FROM t_dives d INNER JOIN t_readers r ON r.pk_user_id = :userId AND r.dive_id = d.pk_dive_id AND d.dive_identifier % :identifier",
            nativeQuery = true)
    Page<DiveEntity> findByIdentifier(long userId, String identifier, Pageable pageable);

    @Modifying
    @Query(
            value =
                    "UPDATE t_dive_profiles SET fk_dive_id = :targetDiveId WHERE pk_dive_profile_id IN (:profileIds)",
            nativeQuery = true)
    void setDiveIdWhereProfileIdIn(
            @Param("targetDiveId") Long targetDiveId, @Param("profileIds") List<Long> profileIds);

    @Query("SELECT MAX(d.number) FROM DiveEntity d WHERE d.user.id = :userId")
    Optional<Integer> findMaxDiveNumberByUserId(long userId);

    @Query(
            value =
                    """
                            SELECT dive.*
                            FROM fuzzy_search_dives_for_user(:query, :userId) AS f(dive, relevance_score)
                            ORDER BY relevance_score DESC
                            """,
            countQuery =
                    """
                            SELECT COUNT(*)
                            FROM fuzzy_search_dives_for_user(:query, :userId) AS f(dive, relevance_score)
                            """,
            nativeQuery = true)
    Page<DiveEntity> searchDives(long userId, String query, Pageable pageable);

    Page<DiveEntity> findByOrderByIdAsc(Pageable pageable);

    Page<DiveEntity> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
