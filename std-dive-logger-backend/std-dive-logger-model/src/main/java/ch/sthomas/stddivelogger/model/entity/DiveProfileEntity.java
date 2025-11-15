package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.DiveProfile;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "t_dive_profile")
public class DiveProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_profile_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_dive_computer_id")
    private DiveComputerEntity computer;

    @Column(name = "dive_profile_start", nullable = false)
    private ZonedDateTime profileStart;

    @Column(name = "dive_profile_end", nullable = false)
    private ZonedDateTime profileEnd;

    @ManyToOne private DiveEntity dive;

    @OneToMany private List<DiveMeasurementEntity> measurements;

    public DiveProfileEntity() {}

    public DiveProfileEntity(
            final DiveComputerEntity computer,
            final Instant start,
            final Instant end,
            final List<DiveMeasurementEntity> measurements) {
        this.computer = computer;
        this.profileStart = start.atZone(UTC);
        this.profileEnd = end.atZone(UTC);
        this.measurements = measurements;
    }

    public DiveProfile toRecord() {
        return new DiveProfile(
                id,
                computer.toRecord(),
                profileStart.toInstant(),
                profileEnd.toInstant(),
                measurements.stream().map(DiveMeasurementEntity::toRecord).toList());
    }
}
