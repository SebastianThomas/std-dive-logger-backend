package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveBuddyNameEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveBuddyNameRepository extends JpaRepository<DiveBuddyNameEntity, Long> {
    Optional<DiveBuddyNameEntity> findByDive_IdAndName(Long diveId, String name);

    /**
     * Returns distinct buddy names from the user's own dives that contain the query string
     * (case-insensitive), ordered by frequency of appearance descending then alphabetically.
     */
    @Query("""
            SELECT b.name
            FROM DiveBuddyNameEntity b
            WHERE b.dive.user.id = :userId
              AND LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
            GROUP BY b.name
            ORDER BY COUNT(b) DESC, b.name ASC
            """)
    List<String> findDistinctBuddyNamesByUser(long userId, String query);
}
