package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.ImportFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ImportFileRepository extends JpaRepository<ImportFileEntity, Long> {

    Optional<ImportFileEntity> findByUserIdAndSha256(long userId, String sha256);

    Optional<ImportFileEntity> findByIdAndUserId(long id, long userId);

    List<ImportFileEntity> findByUserIdOrderByCreatedAtDesc(long userId);

    long countByUserId(long userId);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM ImportFileEntity f WHERE f.userId = :userId")
    long sumSizeBytesByUserId(long userId);

    /**
     * Files no profile, dive or pending import refers to any more, untouched since {@code cutoff}.
     */
    @Query(
            value =
                    """
                    SELECT f.* FROM t_import_file f
                    WHERE f.updated_at < :cutoff
                      AND NOT EXISTS (SELECT 1 FROM t_dive_profile_import_file l
                                      WHERE l.fk_import_file_id = f.pk_import_file_id)
                      AND NOT EXISTS (SELECT 1 FROM t_dive_import_file l
                                      WHERE l.fk_import_file_id = f.pk_import_file_id)
                      AND NOT EXISTS (SELECT 1 FROM t_pending_import p
                                      WHERE p.fk_import_file_id = f.pk_import_file_id)
                    """,
            nativeQuery = true)
    List<ImportFileEntity> findUnreferencedSince(Instant cutoff);
}
