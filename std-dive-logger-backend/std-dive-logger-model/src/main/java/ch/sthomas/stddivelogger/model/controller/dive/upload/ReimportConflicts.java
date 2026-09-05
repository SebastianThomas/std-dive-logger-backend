package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Fields where a reimported file's own value both exists and genuinely disagrees with what's
 * already on the dive - computed by {@code ReimportConflicts.compute(...)}, which applies "prefer
 * non-empty over empty, no-op when equal" automatically first, so only real conflicts land here.
 * Each present field needs the caller to pick a {@code ReimportResolution} choice before commit;
 * absent (null) fields were auto-resolved (or had nothing to resolve) and need no input.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReimportConflicts(
        @Nullable FieldConflict<String> notes,
        @Nullable FieldConflict<Visibility> visibility,
        @Nullable FieldConflict<List<String>> namedBuddies,
        @Nullable FieldConflict<DiveGasConsumption> gasConsumption,
        @Nullable ClockOffset clockOffset) {

    public record FieldConflict<T>(T existing, T reimported) {}

    /**
     * The reimported file's clock is a whole number of hours off the existing profile's - almost
     * certainly a UTC-vs-local-zone artefact, not a different dive. The diver picks which start
     * time to keep via {@code ReimportResolution.startClock}.
     */
    public record ClockOffset(Instant existingStart, Instant reimportedStart, long offsetMinutes) {}

    public boolean hasAny() {
        return notes != null
                || visibility != null
                || namedBuddies != null
                || gasConsumption != null
                || clockOffset != null;
    }
}
