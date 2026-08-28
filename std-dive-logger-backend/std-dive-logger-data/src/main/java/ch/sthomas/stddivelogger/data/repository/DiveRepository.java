package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.entity.CcrUnitEntity;
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
    // EXISTS rather than a JOIN + DISTINCT (the original shape here) - a dive can have more than
    // one profile from the same computer, so a plain join multiplies rows and needs DISTINCT to
    // undo it, but Postgres then rejects sorting by any column (e.g. diveSummary.start, see
    // DiveSortColumn.DATE) that isn't itself in the DISTINCT SELECT list ("for SELECT DISTINCT,
    // ORDER BY expressions must appear in select list"). EXISTS never multiplies rows in the
    // first place, so no DISTINCT is needed and every sort column works. Also now actually scopes
    // by :userId, which the DISTINCT/JOIN version never did - it relied entirely on the caller
    // (DiveService.getDivesByComputer) having already checked computer ownership first.
    @Query(
            "SELECT d FROM DiveEntity d WHERE d.user.id = :userId AND EXISTS (SELECT 1 FROM"
                    + " DiveProfileEntity dp WHERE dp.dive = d AND dp.computer.id = :computerId)")
    Page<DiveEntity> findByUser_IdAndComputer(Long userId, Long computerId, Pageable pageable);

    Page<DiveEntity> findByUser_IdAndConfiguration_Suit_Id(
            long userId, long configurationSuitId, Pageable pageable);

    Page<DiveEntity> findByUser_IdAndConfiguration_CcrUnit_Id(
            long userId, long configurationCcrUnitId, Pageable pageable);

    // Unpaginated variant used only for the "delete every dive using this CCR unit" bulk
    // operation, which needs every matching id up front rather than one page at a time.
    List<DiveEntity> findAllByUser_IdAndConfiguration_CcrUnit_Id(
            long userId, long configurationCcrUnitId);

    Page<DiveEntity> findByUser_Id(long id, Pageable pageable);

    // Unpaginated variant used only by buddy-role stats, which needs every dive up front to
    // aggregate over rather than one page at a time.
    List<DiveEntity> findByUser_Id(long id);

    Page<DiveEntity> findByUser_IdOrderByNumberDesc(Long userId, Pageable pageable);

    @Query(
            "SELECT d FROM DiveEntity d JOIN DiveProfileEntity p ON p.dive = d WHERE p.id IN :profileIds")
    List<DiveEntity> findByProfileIds(List<Long> profileIds);

    /**
     * On-demand per-site dive list for the map popup - see {@code DiveSiteWithDives}'s class doc
     * for why this is fetched lazily rather than always inlined into the site list.
     */
    @Query(
            """
            SELECT new ch.sthomas.stddivelogger.model.dive.BasicDiveInfo(d.id, d.number, d.diveIdentifier)
            FROM DiveEntity d WHERE d.user.id = :userId AND d.diveSite.id = :siteId
            """)
    List<BasicDiveInfo> findBasicDiveInfoByUserIdAndDiveSiteId(long userId, long siteId);

    // Used to gate community-editing of dive site metadata: only users who've actually logged a
    // dive at a site may edit its description/links/type.
    boolean existsByUser_IdAndDiveSite_Id(long userId, long diveSiteId);

    // All of one user's own dives at a given site - for the "set water type for every dive here"
    // bulk backfill action.
    List<DiveEntity> findByUser_IdAndDiveSite_Id(long userId, long diveSiteId);

    // Powers the "smart default" terminology prefill: the user's own most recent explicit
    // BUDDY/TEAM choice, used as the initial pick for a dive that doesn't have one of its own yet.
    Optional<DiveEntity> findFirstByUser_IdAndTeamTerminologyIsNotNullOrderByIdDesc(long userId);

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

    // Spring Data's Pageable-driven sort on a native query only ever appends a raw ORDER BY on a
    // column of the query's own base table - it can't reach a joined table's column, and
    // DiveSortColumn.DATE's value lives on t_dive_summary, not t_dives. findByGroupPrivilege above
    // stays as-is for ID/NUMBER/CUSTOM_IDENTIFIER (all real t_dives columns); this is a dedicated
    // variant for DATE, with the join and ORDER BY spelled out explicitly instead of trying to
    // coerce that into the generic Pageable-sort mechanism.
    // Native-query ORDER BY can't be parametrized directly (Postgres prepared statements don't
    // accept a bind param as ORDER BY direction), so direction is expressed as two CASE arms - only
    // the one matching `ascending` ever produces a non-null value, so only it drives the sort.
    @Query(
            value =
                    """
                            SELECT t_dives.*
                            FROM t_dive_privileges_groups
                            INNER JOIN t_dives
                                ON fk_dive_id = pk_dive_id
                                AND fk_group_id = :groupId
                            LEFT JOIN t_dive_summary ON t_dive_summary.fk_dive_id = pk_dive_id
                            ORDER BY CASE WHEN :ascending THEN t_dive_summary.dive_start END ASC,
                                     CASE WHEN NOT :ascending THEN t_dive_summary.dive_start END DESC
                            """,
            countQuery =
                    "SELECT COUNT(*) FROM t_dive_privileges_groups WHERE fk_group_id = :groupId",
            nativeQuery = true)
    Page<DiveEntity> findByGroupPrivilegeOrderByDiveStart(
            long groupId, boolean ascending, Pageable pageable);

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
            "SELECT MIN(d.number) FROM DiveEntity d WHERE d.user.id = :userId AND d.number > :number")
    Optional<Integer> findNextDiveNumber(long userId, int number);

    @Query(
            "SELECT MAX(d.number) FROM DiveEntity d WHERE d.user.id = :userId AND d.number < :number")
    Optional<Integer> findPreviousDiveNumber(long userId, int number);

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

    Optional<DiveEntity> findByUser_IdAndNumber(Long userId, int number);

    Optional<DiveEntity> findByIdAndUser_Id(Long id, Long userId);

    @Query(
            "SELECT DISTINCT d FROM DiveEntity d JOIN d.tags t WHERE d.user.id = :userId AND t.tag.id = :tagId")
    Page<DiveEntity> findByUser_IdAndTagId(long userId, long tagId, Pageable pageable);

    /**
     * AND-filter: returns only dives that carry ALL of the requested tag IDs. Uses a GROUP BY /
     * HAVING COUNT(DISTINCT) approach so the query is safe regardless of whether individual tags
     * appear multiple times on a dive.
     */
    @Query(
            """
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

    /**
     * A CCR unit is independent of a dive's own {@code BaseConfiguration}, so this applies
     * unconditionally to every dive in {@code idsList}.
     */
    @Query(
            "UPDATE DiveConfigurationEntity c SET c.ccrUnit = :ccrUnitEntity WHERE c.diveId IN (:idsList)")
    @Modifying
    void setCcrUnit(CcrUnitEntity ccrUnitEntity, Collection<Long> idsList);

    @Query(
            "UPDATE DiveConfigurationEntity c SET c.weightKg = :newValue WHERE c.diveId IN (:idsList)")
    @Modifying
    void setWeight(double newValue, Collection<Long> idsList);

    // Used when deleting a CCR unit - the unit itself is never allowed to cascade-delete a dive,
    // only unlink from it (no FK in the schema cascades this, so the DB would otherwise reject
    // the unit's own delete while any configuration still references it).
    @Query("UPDATE DiveConfigurationEntity c SET c.ccrUnit = NULL WHERE c.ccrUnit.id = :ccrUnitId")
    @Modifying(clearAutomatically = true)
    void clearCcrUnitFromConfigurations(long ccrUnitId);

    @Query(
            "UPDATE DiveConfigurationEntity c SET c.secondaryCcrUnit = NULL WHERE c.secondaryCcrUnit.id = :ccrUnitId")
    @Modifying(clearAutomatically = true)
    void clearSecondaryCcrUnitFromConfigurations(long ccrUnitId);
}
