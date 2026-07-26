package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDiveDetailResponse(DivesoftDiveAndMixes diveAndMixes) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DivesoftDiveAndMixes(DivesoftDive dive) {}
}
