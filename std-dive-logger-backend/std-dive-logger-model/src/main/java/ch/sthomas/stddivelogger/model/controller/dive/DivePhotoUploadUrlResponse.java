package ch.sthomas.stddivelogger.model.controller.dive;

/**
 * Response for {@code POST /v1/dives/{id}/photos/upload-url}. {@code uploadUrl} may be an absolute
 * presigned storage URL (production/R2) or a relative, backend-local URL (local dev disk storage,
 * no real presigned-URL concept) - the frontend resolves relative URLs through {@code resolveUrl()}
 * before PUTting, same as it does for other API calls.
 */
public record DivePhotoUploadUrlResponse(long photoId, String uploadUrl) {}
