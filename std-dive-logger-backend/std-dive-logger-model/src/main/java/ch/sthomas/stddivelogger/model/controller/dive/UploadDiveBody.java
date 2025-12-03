package ch.sthomas.stddivelogger.model.controller.dive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.annotation.Nullable;

import java.util.Optional;
import java.util.function.IntSupplier;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UploadDiveBody(
        @Nullable Integer diveNumber, String diveIdentifier, @Nullable Long diveSiteId) {
    public UploadDiveBody withDiveNumber(final IntSupplier diveNumber) {
        return new UploadDiveBody(
                Optional.ofNullable(this.diveNumber).orElseGet(diveNumber::getAsInt),
                diveIdentifier,
                diveSiteId);
    }
}
