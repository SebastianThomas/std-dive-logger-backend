package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.ImportReprocessConflictEntity;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportReprocessConflictRepository
        extends JpaRepository<ImportReprocessConflictEntity, Long> {

    List<ImportReprocessConflictEntity> findByUserIdAndStatusOrderByCreatedAtAscIdAsc(
            long userId, ReprocessConflictStatus status);

    Optional<ImportReprocessConflictEntity> findByIdAndUserId(long id, long userId);

    List<ImportReprocessConflictEntity> findByDiveIdAndProfileIdAndStatus(
            long diveId, long profileId, ReprocessConflictStatus status);

    List<ImportReprocessConflictEntity> findByDiveIdAndFieldAndStatus(
            long diveId, ImportedDiveField field, ReprocessConflictStatus status);

    long countByUserIdAndStatus(long userId, ReprocessConflictStatus status);
}
