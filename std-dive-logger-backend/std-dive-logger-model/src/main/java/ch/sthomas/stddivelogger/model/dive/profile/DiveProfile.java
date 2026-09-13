package ch.sthomas.stddivelogger.model.dive.profile;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record DiveProfile(
        long id,
        DiveComputer diveComputer,
        Instant start,
        Instant end,
        @Nullable List<DiveMeasurementWithId> measurements,
        @Nullable DiveProfileSummary summary,
        // How the device computed this profile's deco / CNS / OTU figures, when the source says.
        @Nullable DecoSettings decoSettings) {
    public DiveProfile(
            final long id,
            final DiveComputer diveComputer,
            final Instant start,
            final Instant end,
            final @Nullable List<DiveMeasurementWithId> measurements,
            final @Nullable DiveProfileSummary summary) {
        this(id, diveComputer, start, end, measurements, summary, null);
    }

    public DiveProfile(
            final long id,
            final DiveComputer diveComputer,
            final Instant start,
            final Instant end,
            final List<DiveMeasurementWithId> measurements,
            final boolean includeMeasurements) {
        this(id, diveComputer, start, end, measurements, includeMeasurements, null);
    }

    public DiveProfile(
            final long id,
            final DiveComputer diveComputer,
            final Instant start,
            final Instant end,
            final List<DiveMeasurementWithId> measurements,
            final boolean includeMeasurements,
            final @Nullable DecoSettings decoSettings) {
        this(
                id,
                diveComputer,
                start,
                end,
                includeMeasurements ? measurements : null,
                getSummary(start, end, measurements),
                decoSettings);
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
                // First/last sample that carries the value, not the literal first/last sample:
                // devices log CNS/GF99/OTU sparser than depth, and a missing reading on the very
                // last sample must not drop the whole end figure.
                firstPresent(measurements, DiveMeasurement::n2),
                lastPresent(measurements, DiveMeasurement::n2),
                lastPresent(measurements, DiveMeasurement::o2Tox),
                firstPresent(measurements, DiveMeasurement::cns),
                lastPresent(measurements, DiveMeasurement::cns));
    }

    private static @Nullable Double firstPresent(
            final List<DiveMeasurementWithId> measurements,
            final Function<DiveMeasurement, @Nullable Double> value) {
        for (final var measurement : measurements) {
            final var v = value.apply(measurement.measurement());
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static @Nullable Double lastPresent(
            final List<DiveMeasurementWithId> measurements,
            final Function<DiveMeasurement, @Nullable Double> value) {
        for (var i = measurements.size() - 1; i >= 0; i--) {
            final var v = value.apply(measurements.get(i).measurement());
            if (v != null) {
                return v;
            }
        }
        return null;
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
