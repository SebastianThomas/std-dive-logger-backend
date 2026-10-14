package ch.sthomas.stddivelogger.model.controller;

import ch.sthomas.stddivelogger.model.dive.profile.TimeRange;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Request body for {@code POST /v1/dives/{id}/profiles/{profileId}/trim}.
 *
 * @param trimStart everything strictly before this is permanently deleted from the profile. {@code
 *     null} to leave the start of the profile untouched.
 * @param trimEnd everything strictly after this is permanently deleted from the profile. {@code
 *     null} to leave the end of the profile untouched.
 * @param cuts sections whose samples are permanently deleted too; each leaves a gap on the real
 *     clock that no longer counts as dive time. {@code null} for none.
 */
public record TrimProfileBody(
        @Nullable Instant trimStart, @Nullable Instant trimEnd, List<TimeRange> cuts) {
    public TrimProfileBody {
        // Absent from a body that only trims the ends.
        cuts = List.copyOf(Objects.requireNonNullElse(cuts, List.of()));
    }
}
