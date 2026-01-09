package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

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

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL)
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
        this.measurements =
                measurements.stream()
                        .map(m -> m.setProfile(this))
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    public DiveProfile toRecord() {
        return toRecord(true);
    }

    public DiveProfile toRecord(final boolean includeMeasurements) {
        return new DiveProfile(
                id,
                computer.toRecord(),
                profileStart.toInstant(),
                profileEnd.toInstant(),
                getMeasurementsStream().map(DiveMeasurementEntity::toRecordWithId).toList(),
                includeMeasurements);
    }

    public DiveProfileEntity setDive(final DiveEntity diveEntity) {
        this.dive = diveEntity;
        return this;
    }

    public long getDiveId() {
        return dive.getId();
    }

    public long getId() {
        return id;
    }

    public Instant getStart() {
        return profileStart.toInstant();
    }

    public Instant getEnd() {
        return profileEnd.toInstant();
    }

    public Duration getBottomTime() {
        final var measurements = getMeasurements();
        return Duration.between(
                measurements.getFirst().toRecord().time(),
                measurements.getLast().toRecord().time());
    }

    public DoubleStream getDepths() {
        return getMeasurementsStream().mapToDouble(DiveMeasurementEntity::getDepth);
    }

     Stream<DiveMeasurementEntity> getMeasurementsStream() {
        return measurements.stream().sorted(Comparator.comparing(DiveMeasurementEntity::getTime));
    }

    List<DiveMeasurementEntity> getMeasurements() {
        return getMeasurementsStream().collect(Collectors.toList());
    }

    public Duration getSurfaceInterval() {
        return null; // TODO
    }

    public void alignProfileManual(final Instant alignToManual) {
        final var prevStart = profileStart;
        final var prevEnd = profileEnd;

        final var diff = Duration.between(prevStart, alignToManual);

        this.profileStart = alignToManual.atOffset(UTC);
        this.profileEnd = prevEnd.plus(diff);
        measurements.forEach(measurement -> measurement.timePlus(diff));
    }
}
