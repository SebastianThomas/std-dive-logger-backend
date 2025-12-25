package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;

import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveSiteRepository extends JpaRepository<DiveSiteEntity, Long> {
    Optional<DiveSiteEntity> findByNameIgnoreCase(String name);

    @Query(
            "SELECT ds FROM DiveSiteEntity ds WHERE LOCATE(LOWER(:name), ds.name) > 0 ORDER BY LOCATE(LOWER(:name), ds.name) * (length(ds.name) - length(:name)) ASC")
    Page<DiveSiteEntity> findByNameContainingOrderedByClosestMatch(String name, Pageable pageable);

    @Query(
            value =
                    """
                            SELECT *
                            FROM t_dive_site
                            WHERE starts_with(name, :name)
                               OR name % :name
                            ORDER BY CASE
                                         WHEN starts_with(name, :name)
                                             THEN 1
                                         ELSE 0
                                         END DESC,
                                     similarity(name, :name) DESC,
                                     LENGTH(name);
                            """,
            countQuery = "SELECT * FROM t_dive_site WHERE starts_with(name, :name) OR name % :name",
            nativeQuery = true)
    Page<DiveSiteEntity> findByClosestMatchName(String name, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM t_dive_site WHERE ST_DWithin(location, :location, :dist) ORDER BY ST_Distance(location, :location)",
            nativeQuery = true)
    List<DiveSiteEntity> findByLocationNear(Point location, double dist);

    @Query(
            value =
                    "SELECT * FROM t_dive_site WHERE ST_DWithin(location, :location, :dist) AND name % :name ORDER BY similarity(name, :name), ST_Distance(location, :location) LIMIT 1",
            nativeQuery = true)
    Optional<DiveSiteEntity> findByLocationNearAndName(Point location, double dist, String name);

    @NativeQuery(
            value =
                    """
                        SELECT d.*, ARRAY_AGG(de.pk_dive_id) AS dive_ids
                        FROM t_dives de
                        INNER JOIN t_dive_site d
                            ON de.fk_diver_id = :userId AND de.dive_site = d.pk_dive_site_id
                        GROUP BY d.pk_dive_site_id
                    """,
            sqlResultSetMapping = "DiveSiteWithIdsMapping")
    List<Object[]> findSitesByDiveWithUserId(long userId);

    @NativeQuery(
            value =
                    """
                        SELECT d.*, ARRAY_AGG(r.dive_id) AS dive_ids
                        FROM t_readers r
                        INNER JOIN t_dives de
                            ON r.pk_user_id = :userId AND r.dive_id = de.pk_dive_id
                        INNER JOIN t_dive_site d ON de.dive_site = d.pk_dive_site_id
                        GROUP BY d.pk_dive_site_id
                    """,
            sqlResultSetMapping = "DiveSiteWithIdsMapping")
    List<Object[]> findSitesByDiveWithReaderUserId(long userId);

    @Query(
            """
                    SELECT COUNT(DISTINCT s.id) FROM DiveSiteEntity s JOIN DiveEntity d ON s.id = d.diveSite.id AND d.user.id = :userId
                    """)
    long countUniqueForUserId(long userId);
}
