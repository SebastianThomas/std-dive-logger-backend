package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "t_dive_profile_history")
public class DiveProfileHistoryEntity {
    @Id
    @Column(name = "fk_dive_profile_id")
    private long diveProfileId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @MapsId
    @JoinColumn(name = "fk_dive_profile_id")
    private DiveProfileEntity diveProfile;

    @Column(name = "original_start")
    private OffsetDateTime originalStart;

    @Column(name = "original_end")
    private OffsetDateTime originalEnd;

    @Column(name = "original_dive_id")
    private long originalDiveId;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public DiveProfileHistoryEntity() {}

    public DiveProfileHistoryEntity(final DiveProfileEntity diveProfileEntity) {
        this(
                diveProfileEntity,
                diveProfileEntity.getDiveId(),
                diveProfileEntity.getStart().atOffset(ZoneOffset.UTC),
                diveProfileEntity.getEnd().atOffset(ZoneOffset.UTC));
    }

    public DiveProfileHistoryEntity(
            final DiveProfileEntity diveProfile,
            final long diveId,
            final OffsetDateTime originalStart,
            final OffsetDateTime originalEnd) {
        this.diveProfile = diveProfile;
        this.originalDiveId = diveId;
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
    }

    public Instant getOriginalStart() {
        return originalStart.toInstant();
    }
}
