package ch.sthomas.stddivelogger.importws.controller;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.ImportService;

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

    public ImportWsController(final ImportService importService) {
        this.importService = importService;
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
}
