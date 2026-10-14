package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;
import ch.sthomas.stddivelogger.model.dive.profile.ProfileCuts;
import ch.sthomas.stddivelogger.model.dive.profile.TimeRange;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DiveProfileUpload(
        long diveComputerId,
        Instant start,
        Instant end,
        List<DiveMeasurement> measurements,
        // How the device computed the profile's deco / CNS / OTU figures, when the source says.
        @Nullable DecoSettings decoSettings,
        // Sections cut out of the profile (see ProfileCuts); absent in staged rows from before.
        List<TimeRange> gaps) {

    public DiveProfileUpload {
        gaps = List.copyOf(Objects.requireNonNullElse(gaps, List.of()));
    }

    public DiveProfileUpload(
            final long diveComputerId,
            final Instant start,
            final Instant end,
            final List<DiveMeasurement> measurements,
            final @Nullable DecoSettings decoSettings) {
        this(diveComputerId, start, end, measurements, decoSettings, List.of());
    }

    public DiveProfileUpload(
            final long diveComputerId,
            final Instant start,
            final Instant end,
            final List<DiveMeasurement> measurements) {
        this(diveComputerId, start, end, measurements, null);
    }

    /**
     * Returns a copy with only the measurements inside {@code [trimStart, trimEnd]} (either bound
     * optional - {@code null} leaves that end untouched) and outside every cut, with {@code
     * start}/{@code end} updated to match the surviving measurements. The pre-commit counterpart of
     * {@code DiveDataService.trimProfile}.
     */
    public DiveProfileUpload trimmed(
            final @Nullable Instant trimStart,
            final @Nullable Instant trimEnd,
            final List<TimeRange> cuts) {
        if (trimStart == null && trimEnd == null && cuts.isEmpty()) {
            return this;
        }
        // A missing bound keeps that whole side, samples outside [start, end] included - like
        // trimming a saved profile, so re-processing re-applies either kind of trim the same way.
        final var kept =
                ProfileCuts.keep(measurements, DiveMeasurement::time, trimStart, trimEnd, cuts);
        return new DiveProfileUpload(
                diveComputerId,
                kept.getFirst().time(),
                kept.getLast().time(),
                kept,
                decoSettings,
                ProfileCuts.gaps(
                        measurements.stream().map(DiveMeasurement::time).toList(),
                        kept.stream().map(DiveMeasurement::time).toList(),
                        cuts,
                        gaps));
    }

    public DiveProfileUpload trimmed(
            final @Nullable Instant trimStart, final @Nullable Instant trimEnd) {
        return trimmed(trimStart, trimEnd, List.of());
    }

    /**
     * Returns a copy with {@code start}/{@code end} and every measurement's time shifted by {@code
     * offset} - a no-op copy when the offset is zero. Used to correct a profile parsed from a
     * source with no timezone of its own (see the Shearwater XML/UDDF/DL7 readers) once the real
     * timezone becomes known, typically only once a dive site is chosen at commit time.
     */
    public DiveProfileUpload shifted(final Duration offset) {
        if (offset.isZero()) {
            return this;
        }
        final var shiftedMeasurements = measurements.stream().map(m -> m.shifted(offset)).toList();
        return new DiveProfileUpload(
                diveComputerId,
                start.plus(offset),
                end.plus(offset),
                shiftedMeasurements,
                decoSettings,
                gaps.stream().map(g -> g.shifted(offset)).toList());
    }
}
