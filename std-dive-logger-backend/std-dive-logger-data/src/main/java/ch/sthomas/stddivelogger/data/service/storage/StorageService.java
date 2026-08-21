package ch.sthomas.stddivelogger.data.service.storage;

import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

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
    default PresignedUpload presignedUploadUrl(
            @NotNull final String path, @NotNull final String contentType, final int expirySeconds)
            throws IOException {
        throw new UnsupportedOperationException(
                "Presigned upload URLs are not supported by this StorageService implementation.");
    }

    /**
     * A genuine stream proxy for reads - the caller is responsible for its own authorization check
     * before calling this (e.g. {@code DivePhotoService#download}), since no signed URL is ever
     * exposed to the browser.
     *
     * <p>Default throws {@link UnsupportedOperationException}; see {@link
     * #presignedUploadUrl(String, String, int)} for why this isn't required of every
     * implementation.
     */
    default InputStream download(@NotNull final String path) throws IOException {
        throw new UnsupportedOperationException(
                "Download is not supported by this StorageService implementation.");
    }

    /**
     * Removes a previously-uploaded object. Same default-throws pattern as {@link
     * #presignedUploadUrl(String, String, int)}/{@link #download(String)} - only the {@code ws}
     * module's implementations need it so far (dive-photo deletion).
     */
    default void delete(@NotNull final String path) throws IOException {
        throw new UnsupportedOperationException(
                "Delete is not supported by this StorageService implementation.");
    }

    String baseUrl();
}
