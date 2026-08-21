package ch.sthomas.stddivelogger.model.controller.dive;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for {@code POST /v1/dives/{id}/photos/{photoId}/confirm}. {@code byteSize} is
 * reported by the frontend (it already knows the {@code File}'s size) rather than measured by the
 * backend, since with a presigned-URL upload the backend never sees the bytes.
 */
public record DivePhotoConfirmBody(@PositiveOrZero long byteSize) {}
