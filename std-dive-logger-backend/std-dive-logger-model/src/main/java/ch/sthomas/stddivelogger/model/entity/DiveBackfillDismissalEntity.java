package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveBackfillField;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One (dive, reason) pair the user has marked "no more info to add" in the backfill guide - kept as
 * its own row rather than a flag on {@code t_dives} so a new backfillable {@link DiveBackfillField}
 * can be bulk-managed for old dives later (a rollout migration inserting/deleting rows), and so a
 * reason with no row surfaces automatically. Structural analog of {@link DiveTagEntity}'s
 * dismissed-auto-tag pattern.
 */
@Entity
@Table(
        name = "t_dive_backfill_dismissal",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_dive_id", "reason"}))
@SuppressWarnings("NullAway.Init")
public class DiveBackfillDismissalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_backfill_dismissal_id", nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_dive_id", nullable = false)
    private DiveEntity dive;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private DiveBackfillField reason;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;

    public DiveBackfillDismissalEntity() {}

    public DiveBackfillDismissalEntity(final DiveEntity dive, final DiveBackfillField reason) {
        this.dive = dive;
        this.reason = reason;
        this.dismissedAt = Instant.now();
    }

    public DiveBackfillField getReason() {
        return reason;
    }

    public Instant getDismissedAt() {
        return dismissedAt;
    }
}
