package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.service.storage.StorageService;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Backs {@code FileStorageService}'s degraded "presigned upload URL" for local disk storage (dev
 * only - production runs on R2, which produces a real presigned URL that never touches this
 * backend). Mirrors the existing multipart-upload pattern used for user icon/background uploads,
 * except the client PUTs raw bytes directly (same shape the real presigned-URL flow uses), rather
 * than a multipart form. Path is validated to stay inside the {@code dive-photos/} prefix generated
 * server-side by {@code DivePhotoService}, so this can't be used to write arbitrary files outside
 * of it.
 */
@RestController
@RequestMapping("/v1/storage")
@Validated
public class LocalStorageUploadController {

    private final StorageService storageService;

    public LocalStorageUploadController(final StorageService storageService) {
        this.storageService = storageService;
    }

    @PutMapping("/local-upload")
    public ResponseEntity<Void> uploadLocal(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("path") @NotBlank final String path,
            final HttpServletRequest request) {
        if (user == null) {
            throw new UnauthorizedException("Log in to upload.");
        }
        if (!path.startsWith("dive-photos/") || path.contains("..")) {
            throw new IllegalArgumentException("Invalid storage path.");
        }
        final var contentType =
                request.getContentType() != null
                        ? request.getContentType()
                        : "application/octet-stream";
        try {
            storageService.upload(
                    path, request.getInputStream(), contentType, request.getContentLength());
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file.", e);
        }
        return ResponseEntity.noContent().build();
    }
}
