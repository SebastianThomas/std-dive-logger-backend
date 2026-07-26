package ch.sthomas.stddivelogger.importws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftConfigResponse;
import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.ImportService;
import ch.sthomas.stddivelogger.service.importer.divesoft.DivesoftConfigService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/import")
public class ImportWsController {

    private final ImportService importService;
    private final DivesoftConfigService divesoftConfigService;

    public ImportWsController(
            final ImportService importService, final DivesoftConfigService divesoftConfigService) {
        this.importService = importService;
        this.divesoftConfigService = divesoftConfigService;
    }

    @Operation(summary = "Add a dive")
    @PostMapping(path = "", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadDiveResult> uploadDive(
            @AuthenticationPrincipal final User user,
            @Nullable @RequestPart("uploadBody") final UploadDiveBody body,
            @RequestParam("file") @NotEmpty final List<MultipartFile> files) {
        final var uploaded = importService.uploadDive(user, files, body);
        if (uploaded.dives().isEmpty() && !uploaded.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(uploaded);
        }
        return ResponseEntity.ok(uploaded);
    }

    @Operation(summary = "Import one or more dives already fetched from the Divesoft/wetnotes API")
    @PostMapping(path = "/divesoft", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadDiveResult> importDivesoft(
            @AuthenticationPrincipal final User user,
            @RequestBody final DivesoftImportRequest request) {
        final var imported = importService.importDivesoft(user, request);
        if (imported.dives().isEmpty() && !imported.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(imported);
        }
        return ResponseEntity.ok(imported);
    }

    @Operation(
            summary =
                    "Get the (non-secret) wetnotes.com Auth0 app config needed to sign in to Divesoft"
                            + " client-side. Never includes a user's actual wetnotes.com credentials.")
    @GetMapping(path = "/divesoft/config")
    public ResponseEntity<DivesoftConfigResponse> getDivesoftConfig() {
        return ResponseEntity.ok(divesoftConfigService.getConfig());
    }
}
