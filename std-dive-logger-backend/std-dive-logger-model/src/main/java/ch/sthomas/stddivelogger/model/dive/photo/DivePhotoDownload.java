package ch.sthomas.stddivelogger.model.dive.photo;

import java.io.InputStream;

/** The streamed bytes + content type for {@code GET /v1/dives/{id}/photos/{photoId}}'s proxy. */
public record DivePhotoDownload(InputStream stream, String contentType) {}
