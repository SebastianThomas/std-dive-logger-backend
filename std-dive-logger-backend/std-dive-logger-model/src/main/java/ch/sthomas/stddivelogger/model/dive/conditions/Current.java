package ch.sthomas.stddivelogger.model.dive.conditions;

import org.jspecify.annotations.Nullable;

// TODO: current strength could alternatively/additionally be suggested from a free ocean-current
// API keyed by dive site coordinates + date, alongside manual entry - left unresearched for now.
public record Current(
        @Nullable Double knots, @Nullable String description, @Nullable Integer feeling) {
    public Current {
        if (knots != null && knots < 0) {
            throw new IllegalArgumentException("Current speed cannot be < 0");
        }
        if (feeling != null && (feeling < 0 || feeling > 5)) {
            throw new IllegalArgumentException("Current feeling must be between 0 and 5");
        }
    }
}
