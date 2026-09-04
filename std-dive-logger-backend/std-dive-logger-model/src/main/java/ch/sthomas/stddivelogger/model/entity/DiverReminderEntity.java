package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.home.DiverReminder;
import ch.sthomas.stddivelogger.model.dive.home.ReminderKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One stored {@link DiverReminder}. Written by the analytics deployable (recomputed daily);
 * upserted on {@code (fk_diver_id, dedupe_key)} so a re-run refreshes the copy while keeping {@link
 * #dismissedAt} / {@link #pushSentAt}. Read back by {@code ws} for {@code GET /v1/home} while it's
 * still within {@link #relevantOn}..{@link #expiresAt} and not dismissed; a nightly job deletes
 * expired rows.
 */
@Entity
@Table(
        name = "t_diver_reminder",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_diver_id", "dedupe_key"}))
@SuppressWarnings("NullAway.Init")
public class DiverReminderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_reminder_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_diver_id", nullable = false)
    private long diverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private ReminderKind kind;

    /** Stable within one "occurrence" so daily re-runs upsert rather than pile up. */
    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "relevant_on", nullable = false)
    private LocalDate relevantOn;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "dive_id")
    private @Nullable Long diveId;

    @Column(name = "years_ago")
    private @Nullable Integer yearsAgo;

    /** false = never web-push this one (e.g. a DORMANT nudge - a banner is enough). */
    @Column(name = "pushable", nullable = false)
    private boolean pushable = true;

    @Column(name = "dismissed_at")
    private @Nullable Instant dismissedAt;

    @Column(name = "push_sent_at")
    private @Nullable Instant pushSentAt;

    public DiverReminderEntity() {}

    public DiverReminderEntity(
            final long diverId,
            final ReminderKind kind,
            final String dedupeKey,
            final LocalDate relevantOn,
            final Instant expiresAt,
            final String title,
            final String body,
            final @Nullable Long diveId,
            final @Nullable Integer yearsAgo) {
        this.diverId = diverId;
        this.kind = kind;
        this.dedupeKey = dedupeKey;
        this.createdAt = Instant.now();
        this.relevantOn = relevantOn;
        this.expiresAt = expiresAt;
        this.title = title;
        this.body = body;
        this.diveId = diveId;
        this.yearsAgo = yearsAgo;
    }

    /** Refresh the copy on a daily re-run without disturbing dismissal / push state. */
    public void refresh(
            final LocalDate relevantOn,
            final Instant expiresAt,
            final String title,
            final String body) {
        this.relevantOn = relevantOn;
        this.expiresAt = expiresAt;
        this.title = title;
        this.body = body;
    }

    public void dismiss() {
        this.dismissedAt = Instant.now();
    }

    public void suppressPush() {
        this.pushable = false;
    }

    public void markPushed() {
        this.pushSentAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getDiverId() {
        return diverId;
    }

    public ReminderKind getKind() {
        return kind;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public @Nullable Long getDiveId() {
        return diveId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isPushable() {
        return pushable;
    }

    public @Nullable Instant getDismissedAt() {
        return dismissedAt;
    }

    public @Nullable Instant getPushSentAt() {
        return pushSentAt;
    }

    public DiverReminder toReminder() {
        return new DiverReminder(id, kind, title, body, diveId, yearsAgo, relevantOn, createdAt);
    }
}
