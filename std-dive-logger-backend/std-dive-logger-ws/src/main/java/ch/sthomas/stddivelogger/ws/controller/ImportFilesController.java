package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.importfile.DiveSourceFile;
import ch.sthomas.stddivelogger.model.importfile.ImportFileInfo;
import ch.sthomas.stddivelogger.model.importfile.ImportFileSettings;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.ImportFileService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** The account's kept dive files (opt-in) - see {@link ImportFileService}. */
@RestController
@RequestMapping("/v1/import-files")
@Validated
public class ImportFilesController {

    public record KeepImportFilesBody(boolean keepImportFiles) {}

    private final ImportFileService importFileService;

    public ImportFilesController(final ImportFileService importFileService) {
        this.importFileService = importFileService;
    }

    @Operation(summary = "Whether uploaded dive files are kept, and how much is stored")
    @GetMapping("/settings")
    public ImportFileSettings settings(@AuthenticationPrincipal final @Nullable User user) {
        return importFileService.getSettings(requireUser(user));
    }

    @Operation(
            summary =
                    "Opt in or out of keeping uploaded dive files; opting out keeps what is"
                            + " stored (delete it separately)")
    @PutMapping(path = "/settings", consumes = APPLICATION_JSON_VALUE)
    public ImportFileSettings updateSettings(
            @AuthenticationPrincipal final @Nullable User user,
            @NotNull @RequestBody final KeepImportFilesBody body) {
        return importFileService.setKeepImportFiles(requireUser(user), body.keepImportFiles());
    }

    @Operation(summary = "Every stored dive file of the account, newest first")
    @GetMapping("")
    public List<ImportFileInfo> list(@AuthenticationPrincipal final @Nullable User user) {
        return importFileService.list(requireUser(user));
    }

    @Operation(summary = "Deletes every stored dive file of the account (the dives stay)")
    @DeleteMapping("")
    public ImportFileSettings deleteAll(@AuthenticationPrincipal final @Nullable User user) {
        final var authenticated = requireUser(user);
        importFileService.deleteAll(authenticated);
        return importFileService.getSettings(authenticated);
    }

    @Operation(
            summary =
                    "A dive's stored source files, with the profiles and dive values each"
                            + " provided")
    @GetMapping("/dives/{diveId}")
    public List<DiveSourceFile> forDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("diveId") @Positive final long diveId) {
        return importFileService.listForDive(requireUser(user), diveId);
    }

    @Operation(summary = "Downloads a stored dive file as it was uploaded")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long id) {
        final var file = importFileService.download(requireUser(user), id);
        return ResponseEntity.ok()
                // Always a download, never rendered: the bytes are the user's own upload.
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.bytes());
    }

    private static User requireUser(final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to manage your stored dive files.");
        }
        return user;
    }
}
