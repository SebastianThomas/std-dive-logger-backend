package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiveBuddyDefaultRoleEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiveBuddyDefaultRoleRepository
        extends JpaRepository<DiveBuddyDefaultRoleEntity, Long> {
    List<DiveBuddyDefaultRoleEntity> findByUser_Id(long userId);

    Optional<DiveBuddyDefaultRoleEntity> findByUser_IdAndBuddyName(long userId, String buddyName);

    Optional<DiveBuddyDefaultRoleEntity> findByUser_IdAndBuddyUser_Id(
            long userId, long buddyUserId);

    List<DiveBuddyDefaultRoleEntity> findByUser_IdAndBuddyNameIn(
            long userId, Collection<String> buddyNames);

    void deleteByUser_IdAndBuddyName(long userId, String buddyName);

    void deleteByUser_IdAndBuddyUser_Id(long userId, long buddyUserId);
}
