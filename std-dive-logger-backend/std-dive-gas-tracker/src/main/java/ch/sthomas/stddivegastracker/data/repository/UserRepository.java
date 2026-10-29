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

    /**
     * Serialises writes to one user's row until the transaction ends. Needed because the upsert's
     * {@code ON CONFLICT} only covers the primary key: two first requests of one user inserting at
     * once could otherwise collide on the unique email instead, and fail.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:userId)", nativeQuery = true)
    int lockUser(long userId);

    /** The email is unique: its previous holder (a stale copy) must lose it first. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE t_user SET email = NULL, updated_at = now() WHERE email = :email AND pk_user_id <> :userId",
            nativeQuery = true)
    int clearEmailOfOthers(String email, long userId);

    /**
     * Inserts at most once, under {@link #lockUser}. Never touches {@code display_name} of an
     * existing row.
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
