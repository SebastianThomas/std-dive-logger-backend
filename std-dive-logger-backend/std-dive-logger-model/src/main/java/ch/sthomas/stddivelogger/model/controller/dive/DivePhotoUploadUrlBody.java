package ch.sthomas.stddivelogger.model.controller.dive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /v1/dives/{id}/photos/upload-url}. contentType is restricted to
 * {@code image/*} - without this, an arbitrary stored content type would let the authenticated
 * download proxy later serve it back as e.g. {@code text/html} from the API's own origin.
 */
public record DivePhotoUploadUrlBody(
        @NotBlank @Pattern(regexp = "^image/[a-zA-Z0-9.+-]+$") String contentType,
        @NotBlank String filename) {}
