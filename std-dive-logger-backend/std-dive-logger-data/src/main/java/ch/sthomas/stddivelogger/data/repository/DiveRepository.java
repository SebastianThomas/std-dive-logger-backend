package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.entity.DiveEntity;
import ch.sthomas.stddivelogger.model.entity.SuitEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiveRepository extends JpaRepository<DiveEntity, Long> {
    @Query(
            "SELECT DISTINCT d FROM DiveEntity d JOIN DiveProfileEntity dp ON dp.dive = d AND dp.computer.id = :computerId")
    Page<DiveEntity> findByUser_IdAndComputer(Long userId, Long computerId, Pageable pageable);

    Page<DiveEntity> findByUser_IdAndConfiguration_Suit_Id(
            long userId, long configurationSuitId, Pageable pageable);

    Page<DiveEntity> findByUser_Id(long id, Pageable pageable);

    Page<DiveEntity> findByUser_IdOrderByNumberDesc(Long userId, Pageable pageable);

    @Query(
            "SELECT d FROM DiveEntity d JOIN DiveProfileEntity p ON p.dive = d WHERE p.id IN :profileIds")
    List<DiveEntity> findByProfileIds(List<Long> profileIds);

    @Query(
            value =
                    """
                            SELECT t_dives.*
                            FROM t_dive_privileges_groups
                            INNER JOIN t_dives
                                ON fk_dive_id = pk_dive_id
                                AND fk_group_id = :groupId
                            """,
            countQuery =
                    "SELECT COUNT(*) FROM t_dive_privileges_groups WHERE fk_group_id = :groupId",
            nativeQuery = true)
    Page<DiveEntity> findByGroupPrivilege(long groupId, Pageable pageable);

    @Query(
            value =
                    "SELECT d.* FROM t_dives d INNER JOIN v_readers r ON r.pk_user_id = :userId AND r.dive_id = d.pk_dive_id AND d.dive_identifier % :name ORDER BY similarity(d.dive_identifier, :identifier) DESC, LENGTH(d.dive_identifier) ASC",
            countQuery =
                    "SELECT COUNT(*) FROM t_dives d INNER JOIN v_readers r ON r.pk_user_id = :userId AND r.dive_id = d.pk_dive_id AND d.dive_identifier % :identifier",
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
            """
                    SELECT MAX(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId
                    """)
    Optional<Long> findMaxDurationByUserId(long userId);

    @Query(
            """
                    SELECT SUM(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId
                    """)
    Optional<Long> findTotalDurationByUserId(long userId);

    @Query(
            """
                    SELECT MAX(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id
                        AND d.user.id = :userId
                        AND YEAR(s.start) = :year
                    """)
    Optional<Long> findMaxDurationByUserIdAndYear(long userId, Integer year);

    @Query(
            """
                    SELECT SUM(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id
                        AND d.user.id = :userId
                        AND YEAR(s.start) = :year
                    """)
    Optional<Long> findTotalDurationByUserIdAndYear(long userId, Integer year);

    @Query(
            """
                    SELECT MAX(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId
                    JOIN DiveBuddyNameEntity b
                    ON d.id = b.dive.id AND b.name = :buddy
                    """)
    Optional<Long> findMaxDurationByUserIdAndBuddy(long userId, String buddy);

    @Query(
            """
                    SELECT SUM(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId
                    JOIN DiveBuddyNameEntity b
                    ON d.id = b.dive.id AND b.name = :buddy
                    """)
    Optional<Long> findTotalDurationByUserIdAndBuddy(long userId, String buddy);

    @Query(
            """
                    SELECT MAX(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId AND d.diveSite.id = :diveSiteId
                    """)
    Optional<Long> findMaxDurationByUserIdAndDiveSiteId(long userId, long diveSiteId);

    @Query(
            """
                    SELECT SUM(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId AND d.diveSite.id = :diveSiteId
                    """)
    Optional<Long> findTotalDurationByUserIdAndDiveSiteId(long userId, long diveSiteId);

    @Query(
            """
                    SELECT MAX(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId AND d.configuration.baseConfiguration = :baseConfiguration
                    """)
    Optional<Long> findMaxDurationByUserIdAndConfiguration_BaseConfiguration(
            long userId, BaseConfiguration baseConfiguration);

    @Query(
            """
                    SELECT SUM(s.durationSeconds)
                    FROM DiveSummaryEntity s
                    JOIN DiveEntity d
                    ON s.dive.id = d.id AND d.user.id = :userId AND d.configuration.baseConfiguration = :baseConfiguration
                    """)
    Optional<Long> findTotalDurationByUserIdAndConfiguration_BaseConfiguration(
            long userId, BaseConfiguration baseConfiguration);

    @Query(
            value =
                    """
                            SELECT (f.dive).*
                            FROM fuzzy_search_dives_for_user(
                                    CAST(:query AS TEXT),
                                    CAST(:userId AS INTEGER))
                                AS f(dive, relevance_score)
                            ORDER BY f.relevance_score DESC
                            """,
            countQuery =
                    """
                            SELECT COUNT(*)
                            FROM fuzzy_search_dives_for_user(CAST(:query AS TEXT), CAST(:userId AS INTEGER)) AS f(dive, relevance_score)
                            """,
            nativeQuery = true)
    Page<DiveEntity> searchDives(long userId, String query, Pageable pageable);

    @Query("SELECT d FROM DiveEntity d WHERE CONCAT(d.number, '') LIKE :query")
    Page<DiveEntity> searchDivesNumeric(long userId, String query, Pageable pageable);

    Page<DiveEntity> findByOrderByIdAsc(Pageable pageable);

    Page<DiveEntity> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    @Query(
            value =
                    """
                            SELECT d.*
                            FROM v_readers r
                            INNER JOIN t_dives d ON r.pk_user_id = :userId
                            WHERE d.pk_dive_id = r.dive_id
                                AND d.pk_dive_id IN (:ids)
                            """,
            nativeQuery = true)
    List<DiveEntity> findAllByIdAndIsReader(long userId, List<Long> ids);

    long countByUser_Id(Long userId);

    @Query(
            value =
                    """
        SELECT COUNT(DISTINCT ns.n) FROM (
            SELECT b.name AS n
            FROM t_dives d
                     INNER JOIN t_dive_buddy_name b ON d.pk_dive_id = b.fk_dive_id
            UNION
            SELECT u.name AS n
            FROM t_users u
                     INNER JOIN t_dives d_other ON d_other.fk_diver_id = u.pk_user_id
                     INNER JOIN t_dive_buddy b ON b.fk_buddy_dive_id = d_other.pk_dive_id
                     INNER JOIN t_dives d_this ON b.fk_dive_id = d_this.pk_dive_id AND d_this.fk_diver_id = :userId
            UNION
            SELECT u.name AS n
            FROM t_dives d_this
                     INNER JOIN t_dive_buddy b ON b.fk_buddy_dive_id = d_this.pk_dive_id AND d_this.fk_diver_id = :userId
                     INNER JOIN t_dives d_other ON d_other.fk_diver_id = b.fk_dive_id
                     INNER JOIN t_users u ON d_other.fk_diver_id = u.pk_user_id
        ) ns
        """,
            nativeQuery = true)
    long countUniqueBuddiesByUserId(long userId);

    Optional<DiveEntity> findByUser_IdAndNumber(Long userId, int number);

    Optional<DiveEntity> findByIdAndUser_Id(Long id, Long userId);

    @Query("SELECT DISTINCT d FROM DiveEntity d JOIN d.tags t WHERE d.user.id = :userId AND t.tag.id = :tagId")
    Page<DiveEntity> findByUser_IdAndTagId(long userId, long tagId, Pageable pageable);

    /**
     * AND-filter: returns only dives that carry ALL of the requested tag IDs.
     * Uses a GROUP BY / HAVING COUNT(DISTINCT) approach so the query is safe
     * regardless of whether individual tags appear multiple times on a dive.
     */
    @Query("""
            SELECT d FROM DiveEntity d
            WHERE d.user.id = :userId
              AND (SELECT COUNT(DISTINCT t2.tag.id)
                   FROM DiveTagEntity t2
                   WHERE t2.dive.id = d.id
                     AND t2.tag.id IN :tagIds) = :tagCount
            """)
    Page<DiveEntity> findByUser_IdAndAllTagIds(
            long userId, Collection<Long> tagIds, long tagCount, Pageable pageable);

    @Query(
            "SELECT d FROM DiveEntity d LEFT JOIN DiveSummaryEntity s ON d.id = s.diveId WHERE s.diveId IS NULL")
    Page<DiveEntity> findByNoSummary(Pageable pageable);

    int countByIdInAndUser_Id(Collection<Long> ids, Long userId);

    @Query(
            "UPDATE DiveConfigurationEntity c SET c.baseConfiguration = :newValue WHERE c.diveId IN (:idsList)")
    @Modifying
    void updateBaseConfiguration(BaseConfiguration newValue, Collection<Long> idsList);

    @Query("UPDATE DiveConfigurationEntity c SET c.suit = :suitEntity WHERE c.diveId IN (:idsList)")
    @Modifying
    void setSuit(SuitEntity suitEntity, Collection<Long> idsList);

    @Query(
            "UPDATE DiveConfigurationEntity c SET c.weightKg = :newValue WHERE c.diveId IN (:idsList)")
    @Modifying
    void setWeight(double newValue, Collection<Long> idsList);

    // ── Tag-filtered stats queries ─────────────────────────────────────────────

    /**
     * Returns IDs of dives owned by the user that carry ALL of the specified tags
     * (non-dismissed). Uses a HAVING COUNT = tagCount to enforce AND semantics.
     */
    @Query("""
            SELECT d.id
            FROM DiveEntity d
            JOIN d.tags dt
            WHERE d.user.id = :userId
              AND dt.dismissed = false
              AND dt.tag.id IN :tagIds
            GROUP BY d.id
            HAVING COUNT(DISTINCT dt.tag.id) = :tagCount
            """)
    List<Long> findDiveIdsByTagsAndUserId(long userId, Collection<Long> tagIds, long tagCount);

    /** Duration stats for a specific tag (dives carrying that tag, non-dismissed). */
    @Query("""
            SELECT MAX(s.durationSeconds)
            FROM DiveSummaryEntity s
            JOIN DiveEntity d ON s.dive.id = d.id AND d.user.id = :userId
            JOIN d.tags dt ON dt.tag.id = :tagId AND dt.dismissed = false
            """)
    Optional<Long> findMaxDurationByUserIdAndTagId(long userId, long tagId);

    @Query("""
            SELECT SUM(s.durationSeconds)
            FROM DiveSummaryEntity s
            JOIN DiveEntity d ON s.dive.id = d.id AND d.user.id = :userId
            JOIN d.tags dt ON dt.tag.id = :tagId AND dt.dismissed = false
            """)
    Optional<Long> findTotalDurationByUserIdAndTagId(long userId, long tagId);

    /** Duration stats for a set of dive IDs (used for tag-combination stats). */
    @Query("""
            SELECT MAX(s.durationSeconds)
            FROM DiveSummaryEntity s
            WHERE s.dive.id IN :diveIds
            """)
    Optional<Long> findMaxDurationByDiveIds(Collection<Long> diveIds);

    @Query("""
            SELECT SUM(s.durationSeconds)
            FROM DiveSummaryEntity s
            WHERE s.dive.id IN :diveIds
            """)
    Optional<Long> findTotalDurationByDiveIds(Collection<Long> diveIds);
}
