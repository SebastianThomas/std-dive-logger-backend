package ch.sthomas.stddivelogger.model.controller.dive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadDiveBody(
        @Nullable Integer diveNumber, @Nullable String diveIdentifier, @Nullable Long diveSiteId) {}
