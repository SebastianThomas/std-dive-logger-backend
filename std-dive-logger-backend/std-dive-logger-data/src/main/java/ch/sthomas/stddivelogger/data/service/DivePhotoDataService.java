package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DivePhotoRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.dive.photo.DivePhoto;
import ch.sthomas.stddivelogger.model.entity.DivePhotoEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DivePhotoDataService {

    private final DivePhotoRepository divePhotoRepository;
    private final DiveRepository diveRepository;
    private final UserRepository userRepository;

    public DivePhotoDataService(
            final DivePhotoRepository divePhotoRepository,
            final DiveRepository diveRepository,
            final UserRepository userRepository) {
        this.divePhotoRepository = divePhotoRepository;
        this.diveRepository = diveRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DivePhoto createPending(
            final long diveId,
            final long userId,
            final String storagePath,
            final String contentType) {
        final var dive =
                diveRepository
                        .findById(diveId)
                        .orElseThrow(() -> new NoSuchElementException("Dive not found: " + diveId));
        final var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        final var entity = new DivePhotoEntity(dive, user, storagePath, contentType);
        return divePhotoRepository.save(entity).toRecord();
    }

    // For server-initiated uploads (e.g. importing from a pasted URL) where the bytes are already
    // fully in hand server-side by the time a row needs to exist at all - unlike the presigned
    // browser-upload flow, there's no "the PUT might never complete" window to guard against here.
    @Transactional
    public DivePhoto createConfirmed(
            final long diveId,
            final long userId,
            final String storagePath,
            final String contentType,
            final long byteSize) {
        final var dive =
                diveRepository
                        .findById(diveId)
                        .orElseThrow(() -> new NoSuchElementException("Dive not found: " + diveId));
        final var user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        final var entity = new DivePhotoEntity(dive, user, storagePath, contentType);
        entity.confirm(byteSize);
        return divePhotoRepository.save(entity).toRecord();
    }

    @Transactional
    public DivePhoto confirm(final long diveId, final long photoId, final long byteSize) {
        final var entity = findOrThrow(diveId, photoId);
        entity.confirm(byteSize);
        return divePhotoRepository.save(entity).toRecord();
    }

    @Transactional(readOnly = true)
    public List<DivePhoto> listConfirmed(final long diveId) {
        return divePhotoRepository.findByDive_IdAndConfirmedTrueOrderByCreatedAtAsc(diveId).stream()
                .map(DivePhotoEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public DivePhotoEntity findConfirmedOrThrow(final long diveId, final long photoId) {
        return divePhotoRepository
                .findByIdAndDive_IdAndConfirmedTrue(photoId, diveId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Photo " + photoId + " not found on dive " + diveId));
    }

    @Transactional(readOnly = true)
    public DivePhotoEntity findOrThrow(final long diveId, final long photoId) {
        return divePhotoRepository
                .findByIdAndDive_Id(photoId, diveId)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Photo " + photoId + " not found on dive " + diveId));
    }

    @Transactional
    public void delete(final DivePhotoEntity entity) {
        divePhotoRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<DivePhotoEntity> findPendingOlderThan(final OffsetDateTime threshold) {
        return divePhotoRepository.findByConfirmedFalseAndCreatedAtBefore(threshold);
    }

    @Transactional
    public void deleteAll(final List<DivePhotoEntity> entities) {
        divePhotoRepository.deleteAll(entities);
    }
}
