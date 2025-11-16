package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "t_refresh_tokens")
public class RefreshTokenEntity {
    @Id
    @Column(name = "jti", nullable = false, unique = true)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public RefreshTokenEntity() {}

    public RefreshTokenEntity(final String jti, final Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt.atOffset(UTC);
    }
}
