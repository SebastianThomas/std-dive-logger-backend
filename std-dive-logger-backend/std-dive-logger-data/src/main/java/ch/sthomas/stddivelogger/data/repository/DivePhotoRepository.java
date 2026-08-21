package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DivePhotoEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DivePhotoRepository extends JpaRepository<DivePhotoEntity, Long> {

    List<DivePhotoEntity> findByDive_IdAndConfirmedTrueOrderByCreatedAtAsc(long diveId);

    Optional<DivePhotoEntity> findByIdAndDive_Id(long id, long diveId);

    Optional<DivePhotoEntity> findByIdAndDive_IdAndConfirmedTrue(long id, long diveId);

    // A row created via requestUploadUrl but never confirmed (the client's direct PUT to storage
    // never completed, or the confirm call never happened) - cleaned up by a scheduled job
    // (DivePhotoService.expireOldPendingUploads) rather than left as a permanent orphan.
    List<DivePhotoEntity> findByConfirmedFalseAndCreatedAtBefore(OffsetDateTime threshold);
}
