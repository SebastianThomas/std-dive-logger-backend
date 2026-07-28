package ch.sthomas.stddivelogger.importws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/v1/import")
@Validated
public class ImportWsController {

    private final ImportService importService;

    public ImportWsController(final ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "Add a dive")
    @PostMapping(path = "", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadDiveResult> uploadDive(
            @AuthenticationPrincipal final User user,
            @Nullable @Valid @RequestPart("uploadBody") final UploadDiveBody body,
            @RequestParam("file") @NotEmpty final List<MultipartFile> files) {
        final var uploaded =
                importService.uploadDive(
                        user,
                        files,
                        Optional.ofNullable(body)
                                .orElse(new UploadDiveBody(null, null, null, null, null)));
        if (uploaded.dives().isEmpty() && !uploaded.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(uploaded);
        }
        return ResponseEntity.ok(uploaded);
    }

    @Operation(summary = "Import one or more dives already fetched from the Divesoft/wetnotes API")
    @PostMapping(path = "/divesoft", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadDiveResult> importDivesoft(
            @AuthenticationPrincipal final User user,
            @Valid @RequestBody final DivesoftImportRequest request) {
        final var imported = importService.importDivesoft(user, request);
        if (imported.dives().isEmpty() && !imported.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(imported);
        }
        return ResponseEntity.ok(imported);
    }
}
