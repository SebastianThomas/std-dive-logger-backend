package ch.sthomas.stddivegastracker.data.repository;

import ch.sthomas.stddivegastracker.model.entity.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    /** The email is unique: its previous holder (a stale copy) must lose it first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE t_user SET email = NULL, updated_at = now() WHERE email = :email AND pk_user_id <> :userId",
            nativeQuery = true)
    int clearEmailOfOthers(String email, long userId);

    /**
     * Race-free: concurrent first requests of one user insert at most once. Never touches {@code
     * display_name} of an existing row.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO t_user (pk_user_id, email, display_name) VALUES (:userId, :email, :displayName)
                    ON CONFLICT (pk_user_id) DO UPDATE SET email = EXCLUDED.email, updated_at = now()
                    WHERE t_user.email IS DISTINCT FROM EXCLUDED.email
                    """,
            nativeQuery = true)
    int upsert(long userId, String email, String displayName);
}
