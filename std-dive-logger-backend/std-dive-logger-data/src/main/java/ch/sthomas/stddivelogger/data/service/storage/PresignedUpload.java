package ch.sthomas.stddivelogger.data.service.storage;

/**
 * A URL the client can {@code PUT} bytes to directly, produced by {@link
 * StorageService#presignedUploadUrl}. May be an absolute presigned object-storage URL, or - for
 * {@code FileStorageService}'s local-disk fallback, which has no real presigned-URL concept - a
 * relative URL pointing at a plain authenticated local upload endpoint instead.
 */
public record PresignedUpload(String url) {}
