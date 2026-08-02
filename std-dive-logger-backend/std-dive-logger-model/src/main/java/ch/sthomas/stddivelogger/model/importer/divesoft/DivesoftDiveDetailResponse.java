package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

// @NotNull is a *runtime* bean-validation check (rejects a malformed request with a 400) - it
// doesn't make the field non-null at the type level, since Jackson deserialization can genuinely
// leave it null for a missing/malformed field before that validation ever runs. @Nullable
// documents that for NullAway/callers, distinct from (and alongside) the runtime check.
@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDiveDetailResponse(
        @NotNull @Valid @Nullable DivesoftDiveAndMixes diveAndMixes) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DivesoftDiveAndMixes(@NotNull @Valid @Nullable DivesoftDive dive) {}
}
