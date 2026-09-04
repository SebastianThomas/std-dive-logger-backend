package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.DiverReminderEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiverReminderRepository extends JpaRepository<DiverReminderEntity, Long> {

    Optional<DiverReminderEntity> findByDiverIdAndDedupeKey(long diverId, String dedupeKey);

    /** A diver's live, not-dismissed reminders, freshest last-relevant first. */
    List<DiverReminderEntity>
            findByDiverIdAndDismissedAtIsNullAndExpiresAtAfterOrderByRelevantOnDescCreatedAtDesc(
                    long diverId, Instant now);

    /**
     * Reminders that still need a web push sent, for users who have at least one push subscription.
     * (The actual send is TODO - see {@code WebPushSender}.)
     */
    @Query(
            """
            SELECT r FROM DiverReminderEntity r
            WHERE r.pushSentAt IS NULL
              AND r.dismissedAt IS NULL
              AND r.pushable = true
              AND r.relevantOn <= :today
              AND r.expiresAt > :now
              AND EXISTS (SELECT 1 FROM PushSubscriptionEntity s WHERE s.userId = r.diverId)
            ORDER BY r.createdAt
            """)
    List<DiverReminderEntity> findDuePushes(
            @Param("today") LocalDate today, @Param("now") Instant now);

    @Modifying
    int deleteByExpiresAtBefore(Instant cutoff);
}
