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
        @Nullable Choice gasConsumption) {

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
