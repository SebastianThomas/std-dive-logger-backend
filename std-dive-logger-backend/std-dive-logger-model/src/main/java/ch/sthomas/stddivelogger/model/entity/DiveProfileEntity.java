package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@SuppressWarnings("NullAway.Init")
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
    @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    private DiveEntity dive;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL)
    private List<DiveMeasurementEntity> measurements;

    @OneToOne(
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true,
            mappedBy = "diveProfile")
    private DiveProfileHistoryEntity diveProfileHistory;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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
                // 0 while this profile hasn't been flushed yet - see
                // DiveConfigurationCylinderEntity.toRecord for why that happens (a brand-new dive
                // converts its own graph to records inside DiveEntity's constructor, to compute
                // cylinder-derived RMV). Real ids are positive, and this transient record is
                // discarded right after; every other caller runs post-flush and is unaffected.
                id == null ? 0L : id,
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

    public DiveComputerEntity getComputer() {
        return computer;
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

    public List<DiveMeasurement> toMeasurementRecords() {
        return getMeasurementsStream().map(DiveMeasurementEntity::toRecord).toList();
    }

    Stream<DiveMeasurementEntity> getMeasurementsStream() {
        return measurements.stream().sorted(Comparator.comparing(DiveMeasurementEntity::getTime));
    }

    List<DiveMeasurementEntity> getMeasurements() {
        return getMeasurementsStream().collect(Collectors.toList());
    }

    public void resetAlignProfileManual() {
        final var originalStart = diveProfileHistory.getOriginalStart();
        alignProfileManual(originalStart);
    }

    public void alignProfileManual(final Instant alignToManual) {
        final var prevStart = profileStart;
        final var prevEnd = profileEnd;

        final var alignToManualODT = alignToManual.atOffset(UTC);
        final var diff = Duration.between(prevStart, alignToManualODT);
        this.profileStart = alignToManualODT;
        this.profileEnd = prevEnd.plus(diff);
        measurements.forEach(measurement -> measurement.timePlus(diff));
    }

    /**
     * Shifts this profile's start/end and every measurement time by {@code delta} (positive =
     * later). Used to re-date a whole dive - e.g. an import whose computer clock was wrong - while
     * keeping every profile's relative offset intact. Does not touch {@link
     * DiveProfileHistoryEntity} so the shift still reads back as a manual-alignment offset
     * (survives reimport, undoable via "reset alignment").
     */
    public void shiftBy(final Duration delta) {
        if (delta.isZero()) {
            return;
        }
        this.profileStart = profileStart.plus(delta);
        this.profileEnd = profileEnd.plus(delta);
        measurements.forEach(measurement -> measurement.timePlus(delta));
    }

    /**
     * Updates just the profile's own start/end bounds, without touching its measurements collection
     * at all - for callers that changed which rows exist via a separate repository call (e.g.
     * trimming) rather than by replacing the whole list, where reassigning every surviving
     * measurement through {@link #replaceMeasurements} would be pure overhead (a redundant merge of
     * every unchanged row) just to update two fields.
     */
    public void updateBounds(final Instant start, final Instant end) {
        this.profileStart = start.atOffset(UTC);
        this.profileEnd = end.atOffset(UTC);
    }

    /**
     * Replaces the raw measurement data for this profile only. Callers are responsible for deleting
     * the previous measurement rows first (no {@code orphanRemoval} on this collection), so
     * reassigning the list here does not by itself clean up the old rows.
     */
    public void replaceMeasurements(
            final List<DiveMeasurementEntity> newMeasurements,
            final Instant start,
            final Instant end) {
        this.profileStart = start.atOffset(UTC);
        this.profileEnd = end.atOffset(UTC);
        this.measurements =
                newMeasurements.stream()
                        .map(m -> m.setProfile(this))
                        .collect(Collectors.toCollection(ArrayList::new));
    }
}
