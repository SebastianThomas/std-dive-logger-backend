package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.DiveProfileSegment;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_profile_segments")
public class DiveProfileSegmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_profile_segment_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_dive_profile_id", nullable = false)
    private DiveProfileEntity profile;

    @Column(name = "first_measurement_idx", nullable = false)
    private int firstMeasurementIdx;

    @Column(name = "last_measurement_idx", nullable = false)
    private int lastMeasurementIdx;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiveProfileSegmentType type;

    public DiveProfileSegmentEntity() {}

    public DiveProfileSegmentEntity(
            final DiveProfileSegment segment, final DiveProfileEntity profile) {
        if (segment.measurements().isEmpty()) {
            throw new IllegalArgumentException("Cannot save empty profiles");
        }
        this.profile = profile;
        this.firstMeasurementIdx = segment.firstMeasurementIdx();
        this.lastMeasurementIdx = segment.firstMeasurementIdx() + segment.measurements().size() - 1;
        this.type = segment.type();
    }

    public long getId() {
        if (id == null) {
            throw new UnsupportedOperationException("Call save before accessing the id.");
        }
        return id;
    }

    public int getFirstMeasurementIdx() {
        return firstMeasurementIdx;
    }

    public int getLastMeasurementIdx() {
        return lastMeasurementIdx;
    }

    public DiveProfileSegmentType getType() {
        return type;
    }

    public DiveProfileEntity getProfile() {
        return profile;
    }
}
