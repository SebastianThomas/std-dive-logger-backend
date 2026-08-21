package ch.sthomas.stddivelogger.model.dive.photo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Metadata for a photo attached to a dive. Deliberately does not carry the storage path - photos
 * are proxy-only (see {@code GET /v1/dives/{id}/photos/{photoId}}), so the frontend never needs a
 * raw storage URL and never gets one.
 */
public record DivePhoto(
        long id,
        long diveId,
        String contentType,
        long byteSize,
        long uploadedByUserId,
        @Nullable String caption,
        @Nullable Instant takenAt,
        Instant createdAt,
        boolean confirmed) {}
