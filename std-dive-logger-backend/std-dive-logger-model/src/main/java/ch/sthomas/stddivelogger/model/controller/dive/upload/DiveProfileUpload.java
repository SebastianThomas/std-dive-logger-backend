package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record DiveProfileUpload(
        long diveComputerId, Instant start, Instant end, List<DiveMeasurement> measurements) {

    /**
     * Returns a copy with only the measurements inside {@code [trimStart, trimEnd]} (either bound
     * optional - {@code null} leaves that end untouched), with {@code start}/{@code end} updated to
     * match the surviving measurements. Mirrors the equivalent trim applied to an already-persisted
     * profile via {@code DiveDataService.trimProfile} - this is the pre-commit counterpart, applied
     * to a staged import that hasn't been saved as a dive yet.
     */
    public DiveProfileUpload trimmed(
            final @Nullable Instant trimStart, final @Nullable Instant trimEnd) {
        if (trimStart == null && trimEnd == null) {
            return this;
        }
        final var effectiveStart = trimStart != null ? trimStart : start;
        final var effectiveEnd = trimEnd != null ? trimEnd : end;
        if (!effectiveStart.isBefore(effectiveEnd)) {
            throw new IllegalArgumentException("Trim start must be before trim end.");
        }
        final var kept =
                measurements.stream()
                        .filter(
                                m ->
                                        !m.time().isBefore(effectiveStart)
                                                && !m.time().isAfter(effectiveEnd))
                        .toList();
        if (kept.size() < 2) {
            throw new IllegalArgumentException(
                    "Trimming this range would leave fewer than 2 measurements on the profile.");
        }
        return new DiveProfileUpload(
                diveComputerId, kept.getFirst().time(), kept.getLast().time(), kept);
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
                diveComputerId, start.plus(offset), end.plus(offset), shiftedMeasurements);
    }
}
