package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveProfile;

import jakarta.persistence.*;

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

    @OneToMany private List<DiveMeasurementEntity> measurements;

    public DiveProfile toRecord() {
        return new DiveProfile(
                id,
                computer.toRecord(),
                profileStart.toInstant(),
                profileEnd.toInstant(),
                measurements.stream().map(DiveMeasurementEntity::toRecord).toList());
    }
}
