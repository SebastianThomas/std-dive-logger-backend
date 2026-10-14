package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.ProfileCuts;
import ch.sthomas.stddivelogger.model.dive.profile.TimeRange;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.entity.converter.DecoSettingsToStringConverter;

import jakarta.persistence.*;
import jakarta.persistence.Convert;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

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

    // Sections cut out of the profile, ascending; see ProfileCuts.
    @ElementCollection
    @CollectionTable(
            name = "t_dive_profile_gap",
            joinColumns = @JoinColumn(name = "fk_dive_profile_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 64)
    private List<ProfileGapEmbeddable> gaps = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = DecoSettingsToStringConverter.class)
    @Column(name = "deco_settings")
    private @Nullable DecoSettings decoSettings;

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
                includeMeasurements,
                decoSettings,
                getGaps());
    }

    public List<TimeRange> getGaps() {
        final var start = getStart();
        return gaps.stream().map(g -> g.toRecord(start)).toList();
    }

    /** Keeps the collection instance - Hibernate tracks it - and only swaps its rows. */
    public DiveProfileEntity replaceGaps(final List<TimeRange> newGaps) {
        final var start = getStart();
        gaps.clear();
        ProfileCuts.merge(newGaps).forEach(g -> gaps.add(new ProfileGapEmbeddable(g, start)));
        return this;
    }

    public @Nullable DecoSettings getDecoSettings() {
        return decoSettings;
    }

    /**
     * Adds what {@code settings} knows to what this profile already has - see {@link
     * DecoSettings#merge}.
     */
    public DiveProfileEntity mergeDecoSettings(final @Nullable DecoSettings settings) {
        this.decoSettings = DecoSettings.merge(decoSettings, settings);
        return this;
    }

    /** Replaces, rather than adds to, what this profile has - a re-processing result. */
    public DiveProfileEntity replaceDecoSettings(final @Nullable DecoSettings settings) {
        this.decoSettings = settings;
        return this;
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
                        measurements.getLast().toRecord().time())
                .minus(ProfileCuts.total(getGaps()));
    }

    /** First to last sample, minus the gaps. */
    public List<TimeRange> getRecordedStretches() {
        final var times = getMeasurementsStream().map(DiveMeasurementEntity::getTime).toList();
        final var stretches = new ArrayList<TimeRange>();
        if (times.isEmpty()) {
            return stretches;
        }
        var from = times.getFirst();
        for (final var gap : getGaps()) {
            if (from.isBefore(gap.start())) {
                stretches.add(new TimeRange(from, gap.start()));
            }
            from = gap.end().isAfter(from) ? gap.end() : from;
        }
        if (from.isBefore(times.getLast())) {
            stretches.add(new TimeRange(from, times.getLast()));
        }
        return stretches;
    }

    public DoubleStream getDepths() {
        return getMeasurementsStream().mapToDouble(DiveMeasurementEntity::getDepth);
    }

    public List<DiveMeasurement> toMeasurementRecords() {
        return getMeasurementsStream().map(DiveMeasurementEntity::toRecord).toList();
    }

    Stream<DiveMeasurementEntity> getMeasurementsStream() {
        return measurements.stream()
                .sorted(Comparator.comparing(DiveMeasurementEntity::getElapsed));
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
    }

    /** Moves the absolute anchor; persisted measurement offsets stay unchanged. */
    public void shiftBy(final Duration delta) {
        if (delta.isZero()) {
            return;
        }
        this.profileStart = profileStart.plus(delta);
        this.profileEnd = profileEnd.plus(delta);
    }

    /**
     * Changes the bounds after trimming while retaining every surviving sample instant; {@code
     * newGaps} are the gaps the trim leaves (see ProfileCuts#gaps).
     */
    public void updateBounds(
            final Instant start, final Instant end, final List<TimeRange> newGaps) {
        final var delta = Duration.between(start, getStart());
        measurements.forEach(m -> m.rebase(delta));
        this.profileStart = start.atOffset(UTC);
        this.profileEnd = end.atOffset(UTC);
        replaceGaps(newGaps);
    }

    /**
     * Replaces the raw measurement data for this profile only. Callers are responsible for deleting
     * the previous measurement rows first (no {@code orphanRemoval} on this collection), so
     * reassigning the list here does not by itself clean up the old rows.
     */
    /**
     * Detaches every current measurement from this profile's collection. Must happen before those
     * rows are deleted: the collection cascades ALL, so a flush would otherwise re-persist (i.e.
     * un-schedule the deletion of) every row still reachable from it.
     */
    public void clearMeasurements() {
        this.measurements = new ArrayList<>();
    }

    /** Fresh samples, with the gaps they have ({@code List.of()} for a whole file's). */
    public void replaceMeasurements(
            final List<DiveMeasurementEntity> newMeasurements,
            final Instant start,
            final Instant end,
            final List<TimeRange> newGaps) {
        this.profileStart = start.atOffset(UTC);
        this.profileEnd = end.atOffset(UTC);
        replaceGaps(newGaps);
        this.measurements =
                newMeasurements.stream()
                        .map(m -> m.setProfile(this))
                        .collect(Collectors.toCollection(ArrayList::new));
    }
}
