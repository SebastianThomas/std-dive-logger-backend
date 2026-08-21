package ch.sthomas.stddivelogger.data.service.storage;

import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Actual object-storage IO (upload/presigned-upload/download/delete) - split out from {@link
 * StorageService} since only {@code ws}'s dive-photo/user-icon/preview-image code paths need it;
 * separating it lets {@code DiveDataService}/{@code AnalyticsDataService} depend on the config-free
 * {@link StorageService} alone, and lets the real (credential-requiring) {@code R2StorageService}
 * implementation stay {@code @Lazy} without breaking apps that merely happen to scan the {@code
 * service} module's classes but never actually call these methods.
 */
public interface ObjectStorageService {

    void upload(
            @NotNull String path,
            @NotNull InputStream output,
            @NotNull String contentType,
            int contentLength)
            throws IOException;

    /**
     * Produces a URL the client can {@code PUT} bytes directly to, valid for {@code expirySeconds}.
     * Avoids routing upload traffic (and storage credentials) through the backend.
     *
     * <p>Default throws {@link UnsupportedOperationException} - only the {@code ws} module's
     * implementations (which back dive-photo uploads) currently need this; the near-duplicate
     * copies in {@code analytics}/{@code import-ws}/{@code autocomplete} don't, so they aren't
     * forced to implement it too.
     */
    PresignedUpload presignedUploadUrl(
            @NotNull final String path, @NotNull final String contentType, final int expirySeconds)
            throws IOException;

    /**
     * A genuine stream proxy for reads - the caller is responsible for its own authorization check
     * before calling this (e.g. {@code DivePhotoService#download}), since no signed URL is ever
     * exposed to the browser.
     *
     * <p>Default throws {@link UnsupportedOperationException}; see {@link
     * #presignedUploadUrl(String, String, int)} for why this isn't required of every
     * implementation.
     */
    InputStream download(@NotNull final String path) throws IOException;

    /**
     * Removes a previously-uploaded object. Same default-throws pattern as {@link
     * #presignedUploadUrl(String, String, int)}/{@link #download(String)} - only the {@code ws}
     * module's implementations need it so far (dive-photo deletion).
     */
    void delete(@NotNull final String path) throws IOException;
}
