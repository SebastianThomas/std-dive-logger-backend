package ch.sthomas.stddivelogger.data.repository;

import ch.sthomas.stddivelogger.model.entity.RefreshTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    boolean existsByJtiAndExpiresAtAfter(String jti, OffsetDateTime expiresAtAfter);

    void deleteAllByExpiresAtBefore(OffsetDateTime expiresAtBefore);

    void deleteByJti(String jti);
}
