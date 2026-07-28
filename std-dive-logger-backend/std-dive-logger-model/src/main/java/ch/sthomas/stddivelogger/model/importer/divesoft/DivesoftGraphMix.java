package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftGraphMix(
        long timestamp, @Nullable String mixO2, @Nullable String mixHe, @Nullable String mixType) {}
