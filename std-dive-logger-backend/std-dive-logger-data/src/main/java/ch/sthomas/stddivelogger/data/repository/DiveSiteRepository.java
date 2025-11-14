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

    @Query("SELECT ds FROM DiveSiteEntity ds WHERE lower(ds.name) LIKE '%' + lower(:name) + '%' ORDER BY length(ds.name) - length(:name) ASC")
    List<DiveSiteEntity> findByNameContainingClosestMatch(String name, Pageable pageable);
}
