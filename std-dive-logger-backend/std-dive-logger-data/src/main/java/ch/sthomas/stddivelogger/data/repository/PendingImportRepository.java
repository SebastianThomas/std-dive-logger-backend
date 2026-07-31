package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PendingImportRepository extends JpaRepository<PendingImportEntity, Long> {

    List<PendingImportEntity> findByUser_IdOrderByCreatedAtDesc(long userId);

    Optional<PendingImportEntity> findByIdAndUser_Id(long id, long userId);

    // Row-locked variant for commit(): without it, two concurrent commits of the same pending
    // import both see it still present (neither has deleted it yet) and both proceed to create a
    // dive from it. PESSIMISTIC_WRITE makes the second commit's SELECT block until the first
    // commit's transaction finishes; by the time it unblocks, the row is already gone (deleted by
    // the first commit), so it correctly comes back empty instead of letting the second commit
    // through to create a duplicate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PendingImportEntity p WHERE p.id = :id AND p.user.id = :userId")
    Optional<PendingImportEntity> findByIdAndUser_IdForUpdate(long id, long userId);

    @Modifying
    @Query("DELETE FROM PendingImportEntity p WHERE p.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
