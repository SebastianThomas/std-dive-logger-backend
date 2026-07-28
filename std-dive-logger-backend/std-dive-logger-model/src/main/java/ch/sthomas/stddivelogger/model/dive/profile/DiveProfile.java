package ch.sthomas.stddivelogger.model.dive.profile;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record DiveProfile(
        long id,
        DiveComputer diveComputer,
        Instant start,
        Instant end,
        @Nullable List<DiveMeasurementWithId> measurements,
        @Nullable DiveProfileSummary summary) {
    public DiveProfile(
            final long id,
            final DiveComputer diveComputer,
            final Instant start,
            final Instant end,
            final List<DiveMeasurementWithId> measurements,
            final boolean includeMeasurements) {
        this(
                id,
                diveComputer,
                start,
                end,
                includeMeasurements ? measurements : null,
                getSummary(start, end, measurements));
    }

    public static DiveProfileSummary getSummary(
            final Instant start,
            final Instant end,
            final List<DiveMeasurementWithId> measurements) {
        final var depths =
                measurements.stream()
                        .map(DiveMeasurementWithId::measurement)
                        .mapToDouble(DiveMeasurement::depth)
                        .summaryStatistics();
        final var duration =
                Duration.between(
                        measurements.getFirst().measurement().time(),
                        measurements.getLast().measurement().time());
        return new DiveProfileSummary(
                start,
                end,
                depths.getAverage(),
                depths.getMax(),
                null,
                duration,
                null,
                null,
                null,
                measurements.getFirst().measurement().n2(),
                measurements.getLast().measurement().n2(),
                measurements.getLast().measurement().o2Tox(),
                measurements.getFirst().measurement().cns(),
                measurements.getLast().measurement().cns());
    }

    @Override
    public @NotNull String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("diveComputer", diveComputer)
                .append("start", start)
                .append("end", end)
                .append(
                        "measurements.size",
                        Optional.ofNullable(measurements).map(List::size).orElse(null))
                .append("summary", summary)
                .toString();
    }
}
