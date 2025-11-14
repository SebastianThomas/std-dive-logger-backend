package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveSiteRepository extends JpaRepository<DiveSiteEntity, Long> {
    Optional<DiveSiteEntity> findByNameIgnoreCase(String name);

    @Query("SELECT ds FROM DiveSiteEntity ds WHERE LOCATE(LOWER(:name), ds.name) > 0 ORDER BY LOCATE(LOWER(:name), ds.name) * (length(ds.name) - length(:name)) ASC")
    List<DiveSiteEntity> findByNameContainingOrderedByClosestMatch(String name, Pageable pageable);
}
