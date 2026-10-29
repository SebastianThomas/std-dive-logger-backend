package ch.sthomas.stddivegastracker.model.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A measured vessel pressure (DOMAIN §2, §6); {@code fillId} is set if a fill produced it. */
@Entity
@Table(name = "t_vessel_reading")
@SuppressWarnings("NullAway.Init")
public class VesselReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_vessel_reading_id", nullable = false)
    private Long id;

    @Column(name = "pressure_bar", nullable = false)
    private double pressureBar;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "fk_fill_id")
    private @Nullable Long fillId;

    @Column(name = "fk_created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public VesselReadingEntity() {}

    public VesselReadingEntity(
            final double pressureBar,
            final Instant measuredAt,
            final @Nullable Long fillId,
            final Long createdByMemberId) {
        this.pressureBar = pressureBar;
        this.measuredAt = measuredAt;
        this.fillId = fillId;
        this.createdByMemberId = createdByMemberId;
    }

    public Long getId() {
        return id;
    }

    public double getPressureBar() {
        return pressureBar;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public @Nullable Long getFillId() {
        return fillId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }
}
