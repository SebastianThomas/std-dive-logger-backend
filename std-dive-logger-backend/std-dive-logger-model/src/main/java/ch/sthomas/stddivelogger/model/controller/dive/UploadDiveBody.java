package ch.sthomas.stddivelogger.model.controller.dive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.Nullable;

import java.time.Duration;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadDiveBody(
        @Nullable Integer diveNumber,
        @Nullable String diveIdentifier,
        @Nullable Long diveSiteId,
        @Nullable Double maxDepth,
        @Nullable Duration duration) {}
