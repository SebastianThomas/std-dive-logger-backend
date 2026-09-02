package ch.sthomas.stddivelogger.model.entity;

import static org.apache.commons.lang3.compare.ComparableUtils.min;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionCalculator;
import ch.sthomas.stddivelogger.model.dive.DiveSummary;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;

import com.nimbusds.jose.util.Pair;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Gatherers;

@Entity
@Table(name = "t_dive_summary")
@SuppressWarnings("NullAway.Init")
public class DiveSummaryEntity {

    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "dive_start")
    private Instant start;

    @Column(name = "dive_end")
    private Instant end;

    @Column(name = "max_depth")
    private double maxDepth;

    // null for a manual-entry dive whose synthetic profile doesn't represent a real depth-time
    // curve, unless the diver explicitly set one - see update() and setAverageDepth() below.
    @Column(name = "avg_depth")
    private @Nullable Double avgDepth;

    @Column(name = "duration_seconds")
    private long durationSeconds;

    @Column(name = "max_time_to_surface_seconds")
    private @Nullable Long maxTimeToSurfaceSeconds;

    // Cylinder-derived RMV, from CylinderConsumptionCalculator. Exactly one is ever non-null on a
    // given dive - ocRmvLiters for an open-circuit dive, bailoutRmvLiters for one with
    // closed-circuit
    // samples - and both are null when no usable cylinder is tracked. Persisted purely so Stats /
    // Trends can aggregate RMV without re-running the calculator per row (see V0_4_10 migration).
    @Column(name = "oc_rmv_liters")
    private @Nullable Double ocRmvLiters;

    @Column(name = "bailout_rmv_liters")
    private @Nullable Double bailoutRmvLiters;

    /**
     * Bump whenever {@link CylinderConsumptionCalculator}'s RMV output changes, so the nightly
     * summary job re-derives every dive whose stored {@link #gasComputationVersion} is behind (a
     * dive save already recomputes on its own - this covers algorithm changes with no data change).
     * Starts at 1; the migration defaults existing rows to 0.
     */
    public static final short GAS_COMPUTATION_VERSION = 1;

    @Column(name = "gas_computation_version")
    private short gasComputationVersion;

    public DiveSummaryEntity() {}

    public DiveSummaryEntity(final DiveEntity dive) {
        this.dive = dive;
        update(dive);
    }

    public void update(final DiveEntity dive) {
        final var profiles = dive.getProfiles();
        final var depths = profiles.stream().flatMapToDouble(DiveProfileEntity::getDepths);
        final var depthSummary = depths.summaryStatistics();
        this.maxDepth = depthSummary.getMax();
        if (!dive.isManualEntryDive()) {
            this.avgDepth = depthSummary.getAverage();
        }
        // Manual dives: leave avgDepth exactly as it already is (null unless the diver explicitly
        // set one via setAverageDepth()) - the synthetic surface/max-depth/surface profile has no
        // real depth-time curve to average, so computing a number from it would be a fabricated
        // guess, not a real average.
        this.start = profiles.getFirst().getStart();
        this.end = profiles.getLast().getEnd();
        this.durationSeconds = getBottomTime(profiles).toSeconds();
        // Across every profile's measurements, regardless of which profile it came from - a
        // twin-computer dive's real TTS peak is whichever profile happened to record the highest
        // reading, not tied to any one profile.
        final var maxTts =
                profiles.stream()
                        .flatMap(DiveProfileEntity::getMeasurementsStream)
                        .map(DiveMeasurementEntity::getTimeToSurfaceSeconds)
                        .filter(Objects::nonNull)
                        .mapToLong(Integer::longValue)
                        .max();
        this.maxTimeToSurfaceSeconds = maxTts.isPresent() ? maxTts.getAsLong() : null;

        final var cylinders =
                Optional.ofNullable(dive.getConfiguration())
                        .map(DiveConfigurationEntity::toRecord)
                        .map(DiveConfiguration::cylinders)
                        .orElse(List.of());
        if (cylinders.isEmpty()) {
            this.ocRmvLiters = null;
            this.bailoutRmvLiters = null;
        } else {
            final var gas =
                    CylinderConsumptionCalculator.calculate(
                            profiles.stream().map(DiveProfileEntity::toRecord).toList(), cylinders);
            this.ocRmvLiters = gas.ocRmvLiters();
            this.bailoutRmvLiters = gas.bailoutRmvLiters();
        }
        this.gasComputationVersion = GAS_COMPUTATION_VERSION;
    }

    /**
     * Lets a diver explicitly record a manual dive's average depth if they know it (there's no real
     * profile to compute it from) - see UpdateDiveBody.averageDepth. Has no effect for a dive with
     * a real computer profile, since update() always overwrites it there anyway.
     */
    public void setAverageDepth(final @Nullable Double averageDepth) {
        this.avgDepth = averageDepth;
    }

    public DiveSummary toRecord() {
        return new DiveSummary(
                start,
                end,
                avgDepth,
                maxDepth,
                null,
                Duration.ofSeconds(durationSeconds),
                maxTimeToSurfaceSeconds != null
                        ? Duration.ofSeconds(maxTimeToSurfaceSeconds)
                        : null);
    }

    private Duration getBottomTime(final List<DiveProfileEntity> profiles) {
        final var bottomTime =
                profiles.stream()
                        .map(DiveProfileEntity::getBottomTime)
                        .reduce(Duration.ZERO, Duration::plus);
        final var overlapToSubtract =
                profiles.stream()
                        .gather(Gatherers.windowSliding(2))
                        // Sliding Window returns all elements if size < 2
                        .filter(l -> l.size() == 2)
                        .map(l -> Pair.of(l.getFirst(), l.getLast()))
                        .filter(
                                p -> {
                                    final var distance =
                                            Duration.between(
                                                    p.getLeft().getStart(),
                                                    p.getRight().getStart());
                                    final var lengthL =
                                            Duration.between(
                                                    p.getLeft().getStart(), p.getLeft().getEnd());
                                    final var lengthR =
                                            Duration.between(
                                                    p.getRight().getStart(), p.getRight().getEnd());
                                    return distance.minus(min(lengthL, lengthR).dividedBy(2))
                                            .isNegative();
                                })
                        .map(p -> min(p.getLeft().getBottomTime(), p.getRight().getBottomTime()))
                        .reduce(Duration.ZERO, Duration::plus);
        return bottomTime.minus(overlapToSubtract);
    }
}
