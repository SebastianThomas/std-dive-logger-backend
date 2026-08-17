package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @Query(
            value = "SELECT * FROM v_readers r WHERE r.dive_id = :diveId",
            countQuery = "SELECT COUNT(*) FROM v_readers WHERE dive_id = :diveId",
            nativeQuery = true)
    Page<UserEntity> findReaders(long diveId, Pageable pageable);

    @Query(value = "SELECT * FROM v_readers r WHERE r.dive_id = :diveId", nativeQuery = true)
    List<UserEntity> findReaders(long diveId);

    @Query(
            value =
                    """
                    SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
                    FROM v_readers r WHERE r.dive_id = :diveId AND r.pk_user_id = :userId
                    """,
            nativeQuery = true)
    boolean isReader(long diveId, long userId);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    void deleteByEmailEqualsIgnoreCase(String email);

    @Query(
            value =
                    """
                            SELECT *
                            FROM t_users
                            WHERE starts_with(name, :name)
                               OR name % :name
                            ORDER BY CASE
                                         WHEN starts_with(name, :name)
                                             THEN 1
                                         ELSE 0
                                         END DESC,
                                     similarity(name, :name) DESC,
                                     LENGTH(name);
                            """,
            countQuery = "SELECT * FROM t_users WHERE starts_with(name, :name) OR name % :name",
            nativeQuery = true)
    Page<UserEntity> findByClosestMatchName(String name, Pageable pageable);

    @Query("UPDATE UserEntity u SET u.verified = TRUE WHERE u.id = :id")
    @Modifying
    void setVerified(long id);

    Optional<UserEntity> findByNameIgnoreCase(String name);
}
