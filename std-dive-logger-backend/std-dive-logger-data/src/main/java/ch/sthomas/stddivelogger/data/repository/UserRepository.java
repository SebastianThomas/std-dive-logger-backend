package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query(value = "SELECT * FROM t_readers r WHERE r.dive_id = :diveId", nativeQuery = true)
    List<UserEntity> findReaders(long diveId);

    @Query(
            value =
                    "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END "
                            + "FROM t_readers r WHERE r.dive_id = :diveId AND r.pk_user_id = :userId",
            nativeQuery = true)
    boolean isReader(long diveId, long userId);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    void deleteByEmailEqualsIgnoreCase(String email);
}
