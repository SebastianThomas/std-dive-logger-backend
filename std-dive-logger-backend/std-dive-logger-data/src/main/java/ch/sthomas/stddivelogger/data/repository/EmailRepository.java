package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.EmailEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmailRepository extends JpaRepository<EmailEntity, Long> {
    @Query(
            value =
                    """
                            UPDATE t_email
                            SET sending = TRUE
                            WHERE sending = false
                              AND sent_at IS NULL
                            RETURNING t_email.*;
                            """,
            nativeQuery = true)
    @Modifying
    List<EmailEntity> updateOutstandingEmailsAndGet();
}
