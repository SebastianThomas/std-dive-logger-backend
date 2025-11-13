package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveSiteEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiveSiteRepository extends JpaRepository<DiveSiteEntity, Long> {
    Optional<DiveSiteEntity> findByNameIgnoreCase(String name);
}
