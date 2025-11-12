package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    @Query(
            "SELECT u FROM UserEntity u JOIN DiveBuddyEntity d ON d.user.id = u.id AND d.dive.id = :diveId")
    List<UserEntity> findBuddies(long diveId);

    @Query(
            value =
                    "SELECT u.* FROM t_users u INNER JOIN t_dive_privileges p ON u.pk_user_id = p.fk_user_id AND p.fk_dive_id = :diveId",
            nativeQuery = true)
    List<UserEntity> findReaders(long diveId);
}
