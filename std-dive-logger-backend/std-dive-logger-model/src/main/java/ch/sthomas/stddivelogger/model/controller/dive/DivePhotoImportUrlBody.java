package ch.sthomas.stddivelogger.model.controller.dive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for {@code POST /v1/dives/{id}/photos/import-url}. */
public record DivePhotoImportUrlBody(@NotBlank @Pattern(regexp = "^https?://.+") String url) {}
