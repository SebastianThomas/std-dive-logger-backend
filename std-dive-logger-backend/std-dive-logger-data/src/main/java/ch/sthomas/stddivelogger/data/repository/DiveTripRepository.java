package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveTripEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveTripRepository extends JpaRepository<DiveTripEntity, Long> {
    List<DiveTripEntity> findByOwner_IdOrderByCreatedAtDesc(long ownerId);
}
