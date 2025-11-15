package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.*;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.ws.services.ImportService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1/dives")
public class StdDiveLoggerController {

    private static final Logger logger = LoggerFactory.getLogger(StdDiveLoggerController.class);

    private final DiveService diveService;
    private final UserService userService;
    private final ImportService importService;

    public StdDiveLoggerController(
            final DiveService diveService,
            final UserService userService,
            final ImportService importService) {
        this.diveService = diveService;
        this.userService = userService;
        this.importService = importService;
    }

    @Operation(summary = "Get Dives for User")
    @GetMapping(path = "")
    public List<Dive> getDivesForUser(@AuthenticationPrincipal final User user) {
        return diveService.getDivesForUser(userService.getUserById(user.id()));
    }

    @Operation(summary = "Create an empty new dive")
    @PostMapping(path = "/create")
    public ResponseEntity<Dive> createDive(
            @Valid @NotNull @RequestBody final UploadDiveBody body,
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (body.fileType() != UploadFileType.NONE) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(diveService.createEmptyDive(user, body));
    }

    @Operation(summary = "Add a dive")
    @PostMapping(path = "/upload", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dive> uploadDive(
            @RequestPart("file") final MultipartFile file,
            @RequestPart("uploadBody") final UploadDiveBody body,
            @AuthenticationPrincipal final User user)
            throws IOException {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (body.fileType() == UploadFileType.NONE) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(importService.uploadDive(user, file, body));
    }

    @Operation(summary = "Update a Dive")
    @PutMapping(path = "", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Dive> updateDive(
            @AuthenticationPrincipal final User user, @NotNull @Valid final Dive dive) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.updateDive(user, dive));
    }

    @Operation(summary = "Merge Dive Profiles")
    @PostMapping(path = "/profiles/merge", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Dive> mergeDiveProfiles(
            @AuthenticationPrincipal final User user,
            final long baseDiveId,
            final long toAddDiveId,
            final boolean keepToAddDive) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.mergeProfiles(user, baseDiveId, toAddDiveId, keepToAddDive));
    }

    public record MoveProfilesRequestBody(List<Long> profileIds, long diveId) {}

    @Operation(summary = "Move Profiles between dives")
    @PostMapping(path = "/profiles/separate", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Dive> moveProfiles(
            @AuthenticationPrincipal final User user, @RequestBody MoveProfilesRequestBody body) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.moveProfiles(user, body.diveId, body.profileIds()));
    }
}
