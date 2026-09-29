package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    List<PushSubscriptionEntity> findByUserId(long userId);

    List<PushSubscriptionEntity> findByUserIdInAndLogbookSyncTrue(Collection<Long> userIds);

    boolean existsByUserId(long userId);

    @Modifying
    int deleteByEndpoint(String endpoint);

    @Modifying
    @Query("DELETE FROM PushSubscriptionEntity s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") long userId);

    @Modifying
    @Query(
            """
            DELETE FROM PushSubscriptionEntity s
            WHERE s.failureCount >= :failures
              AND s.createdAt < :cutoff
              AND (s.lastSuccessAt IS NULL OR s.lastSuccessAt < :cutoff)
            """)
    int deleteFailing(@Param("failures") int failures, @Param("cutoff") Instant cutoff);
}
