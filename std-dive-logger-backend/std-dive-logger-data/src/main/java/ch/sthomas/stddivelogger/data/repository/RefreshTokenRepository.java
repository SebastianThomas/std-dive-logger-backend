package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    boolean existsByJtiAndExpiresAtAfter(String jti, OffsetDateTime expiresAtAfter);

    void deleteAllByExpiresAtBefore(OffsetDateTime expiresAtBefore);

    void deleteByJti(String jti);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") long userId);
}
