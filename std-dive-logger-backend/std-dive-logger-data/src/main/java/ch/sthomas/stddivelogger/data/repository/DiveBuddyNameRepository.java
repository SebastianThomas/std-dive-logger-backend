package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveBuddyNameEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiveBuddyNameRepository extends JpaRepository<DiveBuddyNameEntity, Long> {
    @Query(value = "INSERT INTO t_dive_buddy_name (fk_dive_id, name) VALUES (:vals)", nativeQuery = true)
    void insertAll(long diveId, List<String> buddies);
}
