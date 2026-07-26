package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDiveData(@Nullable Double avgDepth, @Nullable String startMode) {}
