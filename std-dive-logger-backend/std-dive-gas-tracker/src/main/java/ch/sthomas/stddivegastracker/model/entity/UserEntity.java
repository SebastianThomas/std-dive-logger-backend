package ch.sthomas.stddivegastracker.model.entity;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Local copy of a dive-logger user (DOMAIN §2): the id is the dive-logger user id, never generated.
 * Rows are written by the native upsert in {@code UserRepository}; JPA only reads them and updates
 * the display name.
 */
@Entity
@Table(name = "t_user")
@SuppressWarnings("NullAway.Init")
public class UserEntity {

    @Id
    @Column(name = "pk_user_id", nullable = false)
    private Long id;

    /** Null while another user holds this user's last known address (stale, DOMAIN §2). */
    @Column(name = "email", unique = true, length = 320)
    private @Nullable String email;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserEntity() {}

    public Long getId() {
        return id;
    }

    public @Nullable String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
