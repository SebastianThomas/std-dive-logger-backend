package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** One browser's Web Push subscription for a user. A user can have several (phone, laptop, ...). */
@Entity
@Table(
        name = "t_push_subscription",
        uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint"}))
@SuppressWarnings("NullAway.Init")
public class PushSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_push_subscription_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_user_id", nullable = false)
    private long userId;

    @Column(name = "endpoint", nullable = false, columnDefinition = "text")
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth", nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private @Nullable String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private @Nullable Instant lastSuccessAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    public PushSubscriptionEntity() {}

    public PushSubscriptionEntity(
            final long userId,
            final String endpoint,
            final String p256dh,
            final String auth,
            final @Nullable String userAgent) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.failureCount = 0;
    }

    /** Re-subscribing from the same browser: refresh the keys, clear the failure count. */
    public void refresh(final String p256dh, final String auth, final @Nullable String userAgent) {
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
        this.failureCount = 0;
    }

    public void recordSuccess() {
        this.lastSuccessAt = Instant.now();
        this.failureCount = 0;
    }

    public void recordFailure() {
        this.failureCount++;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public int getFailureCount() {
        return failureCount;
    }
}
