package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.PendingImportRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PendingImportDataService {

    private final PendingImportRepository pendingImportRepository;
    private final UserRepository userRepository;

    public PendingImportDataService(
            final PendingImportRepository pendingImportRepository,
            final UserRepository userRepository) {
        this.pendingImportRepository = pendingImportRepository;
        this.userRepository = userRepository;
    }

    public PendingImportEntity save(
            final User user,
            final PendingImportSource source,
            final @Nullable String externalId,
            final @Nullable String filename,
            final @Nullable String diveIdentifierGuess,
            final @Nullable String siteNameGuess,
            final @Nullable Double latitudeGuess,
            final @Nullable Double longitudeGuess,
            final @Nullable String computerSerial,
            final @Nullable Instant startDate,
            final @Nullable Long durationSeconds,
            final @Nullable Double maxDepth,
            final PendingImportPayload payload) {
        final var userEntity = userRepository.findById(user.id()).orElseThrow();
        return pendingImportRepository.save(
                new PendingImportEntity(
                        userEntity,
                        source,
                        externalId,
                        filename,
                        diveIdentifierGuess,
                        siteNameGuess,
                        latitudeGuess,
                        longitudeGuess,
                        computerSerial,
                        startDate,
                        durationSeconds,
                        maxDepth,
                        payload,
                        Instant.now()));
    }

    @Transactional
    public PendingImportEntity markReimportTarget(
            final long pendingImportId, final long diveId, final long profileId) {
        final var entity = pendingImportRepository.findById(pendingImportId).orElseThrow();
        entity.withReimportTarget(diveId, profileId);
        return pendingImportRepository.save(entity);
    }

    public List<PendingImportEntity> findByUser(final User user) {
        return pendingImportRepository.findByUser_IdOrderByCreatedAtDesc(user.id());
    }

    public Optional<PendingImportEntity> findByIdAndUser(final long id, final User user) {
        return pendingImportRepository.findByIdAndUser_Id(id, user.id());
    }

    /**
     * Same lookup as {@link #findByIdAndUser}, but row-locked for use immediately before
     * committing/deleting a pending import - see {@link
     * PendingImportRepository#findByIdAndUser_IdForUpdate} for why this matters. Must be called
     * from within an active transaction.
     */
    public Optional<PendingImportEntity> findByIdAndUserForCommit(final long id, final User user) {
        return pendingImportRepository.findByIdAndUser_IdForUpdate(id, user.id());
    }

    public void deleteById(final long id) {
        pendingImportRepository.deleteById(id);
    }

    public int deleteOlderThan(final Instant cutoff) {
        return pendingImportRepository.deleteByCreatedAtBefore(cutoff);
    }
}
