package ch.sthomas.stddivelogger.importws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportCommitRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.StageImportResult;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/import")
@Validated
public class ImportWsController {

    private final ImportService importService;

    public ImportWsController(final ImportService importService) {
        this.importService = importService;
    }

    @Operation(
            summary =
                    "Stage one or more dive files for import - parses them but does not persist"
                            + " anything yet. Review the returned summaries and call the commit"
                            + " endpoint per pending import to actually save it.")
    @PostMapping(path = "", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StageImportResult> stageUpload(
            @AuthenticationPrincipal final User user,
            @RequestParam("file") @NotEmpty final List<MultipartFile> files) {
        final var staged = importService.stageUpload(user, files);
        if (staged.staged().isEmpty() && !staged.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(staged);
        }
        return ResponseEntity.ok(staged);
    }

    @Operation(
            summary =
                    "Stage one or more dives already fetched from the Divesoft/wetnotes API -"
                            + " parses them but does not persist anything yet.")
    @PostMapping(path = "/divesoft", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<StageImportResult> stageDivesoft(
            @AuthenticationPrincipal final User user,
            @Valid @RequestBody final DivesoftImportRequest request) {
        final var staged = importService.stageDivesoft(user, request);
        if (staged.staged().isEmpty() && !staged.errors().isEmpty()) {
            return ResponseEntity.internalServerError().body(staged);
        }
        return ResponseEntity.ok(staged);
    }

    @Operation(summary = "List the current user's pending (not yet committed) imports")
    @GetMapping(path = "/pending")
    public List<PendingImportSummary> listPending(@AuthenticationPrincipal final User user) {
        return importService.listPending(user);
    }

    @Operation(
            summary =
                    "Full profile data (including measurements) for a pending import, for"
                            + " previewing/trimming before commit - not returned at stage time"
                            + " itself to avoid round-tripping full data unless actually needed.")
    @GetMapping(path = "/pending/{id}/preview")
    public List<DiveProfile> previewPending(
            @AuthenticationPrincipal final User user, @PathVariable("id") @Positive final long id) {
        return importService.previewPending(user, id);
    }

    @Operation(
            summary =
                    "Commit a pending import: applies the given overrides (dive site, identity"
                            + " fields, or attach to an existing dive instead) and persists the"
                            + " dive, discarding the pending import row.")
    @PostMapping(path = "/pending/{id}/commit", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<SimplifiedDive> commit(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long id,
            @Valid @RequestBody final PendingImportCommitRequest overrides) {
        return ResponseEntity.ok(importService.commit(user, id, overrides));
    }

    @Operation(summary = "Discard a pending import without persisting it")
    @DeleteMapping(path = "/pending/{id}")
    public ResponseEntity<Void> discard(
            @AuthenticationPrincipal final User user, @PathVariable("id") @Positive final long id) {
        importService.discard(user, id);
        return ResponseEntity.noContent().build();
    }
}
