package ch.sthomas.stddivelogger.model.controller.dive.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

/**
 * The caller's choice for each field {@link ReimportConflicts} flagged - required only for fields
 * that were actually conflicting; leave a field null if its own {@code ReimportConflicts} entry was
 * null (nothing to resolve, or already auto-resolved).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReimportResolution(
        @Nullable Choice notes,
        @Nullable Choice visibility,
        @Nullable BuddiesChoice namedBuddies,
        @Nullable Choice gasConsumption,
        // Which start time to keep when the reimport's clock is a whole number of hours off the
        // existing profile (see ReimportConflicts.ClockOffset). EXISTING re-aligns the freshly
        // parsed data onto the dive's current clock; NEW adopts the uploaded file's clock.
        @Nullable Choice startClock) {

    public enum Choice {
        EXISTING,
        NEW
    }

    public enum BuddiesChoice {
        EXISTING,
        NEW,
        UNION
    }
}
