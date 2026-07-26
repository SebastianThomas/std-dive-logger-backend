package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftMix(
        @Nullable Integer id,
        @Nullable String o2,
        @Nullable String he,
        @Nullable Double startPressure,
        @Nullable Double endPressure,
        @Nullable Double tankVolume,
        @Nullable String mixType,
        @Nullable String tankType) {}
