package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveBuddyEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiveBuddyRepository extends JpaRepository<DiveBuddyEntity, Long> {

    /**
     * The link row between two dives, regardless of which one is the DB's lower-id ({@code dive})
     * side and which is the higher-id ({@code buddyDive}) side - callers generally only know "two
     * dive ids", not the storage ordering.
     */
    @Query(
            "SELECT b FROM DiveBuddyEntity b WHERE (b.dive.id = :diveIdA AND b.buddyDive.id ="
                    + " :diveIdB) OR (b.dive.id = :diveIdB AND b.buddyDive.id = :diveIdA)")
    Optional<DiveBuddyEntity> findLink(long diveIdA, long diveIdB);

    /**
     * Every link between a dive {@code userId} owns and a dive {@code buddyUserId} owns, regardless
     * of which side is which - used to bulk-set a role for "this buddy" across every dive shared
     * with them.
     */
    @Query(
            "SELECT b FROM DiveBuddyEntity b WHERE (b.dive.user.id = :userId AND"
                    + " b.buddyDive.user.id = :buddyUserId) OR (b.dive.user.id = :buddyUserId AND"
                    + " b.buddyDive.user.id = :userId)")
    List<DiveBuddyEntity> findAllLinksBetweenUsers(long userId, long buddyUserId);

    // Two plain halves rather than one query with an entity-valued CASE WHEN in the SELECT list -
    // this Hibernate version can't handle the latter (ClassCastException at execution). Combined
    // and de-duplicated in DiveDataService.findLinkedBuddyUsersForUser.
    List<DiveBuddyEntity> findByDive_User_Id(long userId);

    List<DiveBuddyEntity> findByBuddyDive_User_Id(long userId);
}
