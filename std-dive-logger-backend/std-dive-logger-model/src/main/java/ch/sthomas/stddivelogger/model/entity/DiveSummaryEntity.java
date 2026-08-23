package ch.sthomas.stddivelogger.model.entity;

import static org.apache.commons.lang3.compare.ComparableUtils.min;

import ch.sthomas.stddivelogger.model.dive.DiveSummary;

import com.nimbusds.jose.util.Pair;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

    @Column(name = "avg_depth")
    private double avgDepth;

    @Column(name = "duration_seconds")
    private long durationSeconds;

    @Column(name = "max_time_to_surface_seconds")
    private @Nullable Long maxTimeToSurfaceSeconds;

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
        this.avgDepth = depthSummary.getAverage();
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
