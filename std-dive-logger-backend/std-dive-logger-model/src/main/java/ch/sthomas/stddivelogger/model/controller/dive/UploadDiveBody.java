package ch.sthomas.stddivelogger.model.controller.dive;

import jakarta.annotation.Nullable;

import java.util.Optional;
import java.util.function.IntSupplier;

public record UploadDiveBody(
        @Nullable Integer diveNumber,
        String diveIdentifier,
        @Nullable Long diveSiteId,
        UploadFileType fileType) {
    public UploadDiveBody withDiveNumber(final IntSupplier diveNumber) {
        return new UploadDiveBody(
                Optional.ofNullable(this.diveNumber).orElseGet(diveNumber::getAsInt),
                diveIdentifier,
                diveSiteId,
                fileType);
    }
}
