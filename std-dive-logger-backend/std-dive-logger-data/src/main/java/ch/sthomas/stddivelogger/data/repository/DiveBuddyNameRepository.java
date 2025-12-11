package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveBuddyNameEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiveBuddyNameRepository extends JpaRepository<DiveBuddyNameEntity, Long> {
    Optional<DiveBuddyNameEntity> findByDive_IdAndName(Long diveId, String name);
}
