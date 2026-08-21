package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoConfirmBody;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoImportUrlBody;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlBody;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlResponse;
import ch.sthomas.stddivelogger.model.dive.photo.DivePhoto;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DivePhotoService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dive photo gallery (WS4): presigned-upload-then-confirm for writes, and a proxied, authenticated
 * download for reads (photos are private/proxy-only - never a public URL).
 */
@RestController
@RequestMapping("/v1/dives/{id}/photos")
@Validated
public class DivePhotoController {

    private final DivePhotoService divePhotoService;

    public DivePhotoController(final DivePhotoService divePhotoService) {
        this.divePhotoService = divePhotoService;
    }

    @Operation(summary = "Request a URL to upload a new dive photo to")
    @PostMapping("/upload-url")
    public DivePhotoUploadUrlResponse requestUploadUrl(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @Valid @NotNull @RequestBody final DivePhotoUploadUrlBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to upload a photo.");
        }
        return divePhotoService.requestUploadUrl(user, diveId, body);
    }

    @Operation(summary = "Confirm a dive photo upload has completed")
    @PostMapping("/{photoId}/confirm")
    public DivePhoto confirmUpload(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("photoId") @Positive final long photoId,
            @Valid @NotNull @RequestBody final DivePhotoConfirmBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to confirm a photo upload.");
        }
        return divePhotoService.confirm(user, diveId, photoId, body.byteSize());
    }

    @Operation(
            summary = "Import photo(s) from a pasted URL",
            description =
                    "Fetched server-side (avoids browser CORS restrictions on third-party hosts"
                            + " like OneDrive/Jottacloud share links). A single image URL becomes"
                            + " one photo; a .zip archive is unpacked into one photo per image"
                            + " entry.")
    @PostMapping("/import-url")
    public List<DivePhoto> importFromUrl(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @Valid @NotNull @RequestBody final DivePhotoImportUrlBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to import a photo.");
        }
        return divePhotoService.importFromUrl(user, diveId, body.url());
    }

    @Operation(summary = "List a dive's photos")
    @GetMapping
    public List<DivePhoto> listPhotos(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view dive photos.");
        }
        return divePhotoService.list(user, diveId);
    }

    @Operation(summary = "Download a dive photo (authenticated proxy - photos are never public)")
    @GetMapping("/{photoId}")
    public ResponseEntity<InputStreamResource> downloadPhoto(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("photoId") @Positive final long photoId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view dive photos.");
        }
        final var download = divePhotoService.download(user, diveId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                // Belt-and-braces alongside the image/* upload restriction: even a legitimately
                // image/* response should never render inline as if it were page content.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new InputStreamResource(download.stream()));
    }

    @Operation(summary = "Delete a dive photo")
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("photoId") @Positive final long photoId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete a photo.");
        }
        divePhotoService.delete(user, diveId, photoId);
        return ResponseEntity.noContent().build();
    }
}
