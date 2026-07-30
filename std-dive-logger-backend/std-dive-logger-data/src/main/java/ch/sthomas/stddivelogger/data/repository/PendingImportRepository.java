package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.PendingImportEntity;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Modifying
    @Query("DELETE FROM PendingImportEntity p WHERE p.createdAt < :cutoff")
    int deleteByCreatedAtBefore(Instant cutoff);
}
