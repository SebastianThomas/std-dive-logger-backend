package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDiveDetailResponse(@NotNull @Valid DivesoftDiveAndMixes diveAndMixes) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DivesoftDiveAndMixes(@NotNull @Valid DivesoftDive dive) {}
}
