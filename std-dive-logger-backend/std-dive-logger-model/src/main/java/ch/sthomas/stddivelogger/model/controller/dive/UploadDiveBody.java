package ch.sthomas.stddivelogger.model.controller.dive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadDiveBody(
        @Nullable @Positive Integer diveNumber,
        @Nullable String diveIdentifier,
        @Nullable @Positive Long diveSiteId,
        @Nullable @Positive Double maxDepth,
        @Nullable Duration duration) {}
