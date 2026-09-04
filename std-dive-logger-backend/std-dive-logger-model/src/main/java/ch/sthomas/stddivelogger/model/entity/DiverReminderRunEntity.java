package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Bookkeeping for {@link DiverReminderEntity}: the last day the analytics deployable recomputed a
 * diver's reminders, and the dive fingerprint it saw then. Reminders are recomputed when the day
 * rolls over (anniversaries move) or the fingerprint changes (a dive was added / re-dated /
 * imported). Mirrors {@code t_diver_activity_stats}'s staleness bookkeeping.
 */
@Entity
@Table(name = "t_diver_reminder_run")
@SuppressWarnings("NullAway.Init")
public class DiverReminderRunEntity {

    @Id
    @Column(name = "fk_diver_id")
    private Long diverId;

    @Column(name = "computed_on", nullable = false)
    private LocalDate computedOn;

    @Column(name = "source_fingerprint", nullable = false)
    private String sourceFingerprint;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public DiverReminderRunEntity() {}

    public DiverReminderRunEntity(
            final long diverId, final LocalDate computedOn, final String sourceFingerprint) {
        this.diverId = diverId;
        this.computedOn = computedOn;
        this.sourceFingerprint = sourceFingerprint;
        this.computedAt = Instant.now();
    }

    public void update(final LocalDate computedOn, final String sourceFingerprint) {
        this.computedOn = computedOn;
        this.sourceFingerprint = sourceFingerprint;
        this.computedAt = Instant.now();
    }

    public Long getDiverId() {
        return diverId;
    }

    public LocalDate getComputedOn() {
        return computedOn;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }
}
