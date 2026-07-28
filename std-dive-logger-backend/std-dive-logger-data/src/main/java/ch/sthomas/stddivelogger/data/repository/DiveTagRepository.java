package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveTagEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface DiveTagRepository extends JpaRepository<DiveTagEntity, Long> {

    @Modifying
    @Query("DELETE FROM DiveTagEntity t WHERE t.dive.id = :diveId")
    void deleteAllByDiveId(long diveId);

    /** Deletes only active (non-dismissed, non-manual) auto-detected tag rows. */
    @Modifying
    @Query(
            "DELETE FROM DiveTagEntity t WHERE t.dive.id = :diveId AND t.manual = false AND t.dismissed = false")
    void deleteActiveAutoTagsByDiveId(long diveId);

    /**
     * Returns the tag-definition IDs that are already "covered" for a given dive — i.e. rows that
     * are either manual or dismissed and must not be overwritten.
     */
    @Query(
            "SELECT t.tag.id FROM DiveTagEntity t WHERE t.dive.id = :diveId AND (t.manual = true OR t.dismissed = true)")
    Set<Long> findCoveredTagIdsByDiveId(long diveId);

    /**
     * Returns [tagId, count] pairs: for each tag the number of the given user's dives that carry it
     * (excluding dismissed rows). Used to surface usage counts in the UI.
     */
    @Query(
            "SELECT t.tag.id, COUNT(t) FROM DiveTagEntity t WHERE t.dive.user.id = :userId AND t.dismissed = false GROUP BY t.tag.id")
    List<Object[]> countTagUsageForUser(long userId);
}
