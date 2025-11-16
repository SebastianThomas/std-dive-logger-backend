package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveSiteRepository extends JpaRepository<DiveSiteEntity, Long> {
    Optional<DiveSiteEntity> findByNameIgnoreCase(String name);

    @Query(
            "SELECT ds FROM DiveSiteEntity ds WHERE LOCATE(LOWER(:name), ds.name) > 0 ORDER BY LOCATE(LOWER(:name), ds.name) * (length(ds.name) - length(:name)) ASC")
    List<DiveSiteEntity> findByNameContainingOrderedByClosestMatch(String name, Pageable pageable);

    @Query(
            value =
                    "SELECT * FROM t_dive_site WHERE name % :name ORDER BY similarity(name, :name) DESC, LENGTH(name) ASC",
            countQuery = "SELECT * FROM t_dive_site WHERE name % :name",
            nativeQuery = true)
    List<DiveSiteEntity> findByClosestMatchName(String name, Pageable pageable);

    @Query(
            value = "SELECT * FROM t_dive_site WHERE ST_DWithin(location, :location, :dist)",
            nativeQuery = true)
    List<DiveSiteEntity> findByLocationNear(Coordinate location, double dist);
}
