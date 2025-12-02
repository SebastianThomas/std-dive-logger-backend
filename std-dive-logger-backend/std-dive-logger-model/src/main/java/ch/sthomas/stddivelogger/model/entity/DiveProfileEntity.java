package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.DiveProfile;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "t_dive_profiles")
public class DiveProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_profile_id", nullable = false)
    private Long id;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_dive_computer")
    private DiveComputerEntity computer;

    @Column(name = "dive_profile_start", nullable = false)
    private OffsetDateTime profileStart;

    @Column(name = "dive_profile_end", nullable = false)
    private OffsetDateTime profileEnd;

    @JoinColumn(name = "fk_dive_id")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private DiveEntity dive;

    @JoinColumn(name = "fk_dive_profile_id")
    @OneToMany(cascade = CascadeType.ALL)
    private List<DiveMeasurementEntity> measurements;

    public DiveProfileEntity() {}

    public DiveProfileEntity(
            final DiveComputerEntity computer,
            final Instant start,
            final Instant end,
            final List<DiveMeasurementEntity> measurements) {
        this.computer = computer;
        this.profileStart = start.atOffset(UTC);
        this.profileEnd = end.atOffset(UTC);
        this.measurements = measurements;
    }

    public DiveProfile toRecord() {
        return new DiveProfile(
                id,
                computer.toRecord(),
                profileStart.toInstant(),
                profileEnd.toInstant(),
                measurements.stream().map(DiveMeasurementEntity::toRecord).toList(),
                null);
    }
}
