package ch.sthomas.stddivelogger.model.controller.dive;

import jakarta.annotation.Nullable;

public record UploadDiveBody(
        int diveNumber,
        String diveIdentifier,
        @Nullable Long diveSiteId,
        UploadFileType fileType) {}
