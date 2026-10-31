package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.model.scrubber.StoredCanister;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One scoop of absorbent for a member, an estimate in kg (DOMAIN §12); deleted if wrong. */
@Entity
@Table(name = "t_scrubber_use")
@SuppressWarnings("NullAway.Init")
public class ScrubberUseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_scrubber_use_id", nullable = false)
    private Long id;

    @Column(name = "fk_member_id", nullable = false)
    private Long memberId;

    @Column(name = "kg", nullable = false)
    private double kg;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "fk_recorded_by_member_id", nullable = false)
    private Long recordedByMemberId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ScrubberUseEntity() {}

    public ScrubberUseEntity(
            final Long memberId,
            final double kg,
            final Instant usedAt,
            final Long recordedByMemberId) {
        this.memberId = memberId;
        this.kg = kg;
        this.usedAt = usedAt;
        this.recordedByMemberId = recordedByMemberId;
    }

    public Long getId() {
        return id;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public StoredCanister.Use toRecord() {
        return new StoredCanister.Use(id, memberId, kg, usedAt, recordedByMemberId);
    }
}
