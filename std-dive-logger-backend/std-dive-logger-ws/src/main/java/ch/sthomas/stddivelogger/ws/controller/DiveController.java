package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.*;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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
public class DiveController {

    private static final Logger logger = LoggerFactory.getLogger(DiveController.class);

    private final DiveService diveService;
    private final UserService userService;
    private final ImportService importService;

    public DiveController(
            final DiveService diveService,
            final UserService userService,
            final ImportService importService) {
        this.diveService = diveService;
        this.userService = userService;
        this.importService = importService;
    }

    @Operation(summary = "Get Dives for User")
    @GetMapping(path = "")
    public ResponseEntity<List<SimplifiedDive>> getDivesForUser(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.getDivesForUser(userService.getUserById(user.id()), page));
    }

    @Operation(summary = "Get Dive by ID")
    @GetMapping(path = "/{id}")
    public ResponseEntity<Dive> getDiveById(
            @AuthenticationPrincipal final User user, @PathVariable("id") final Long id) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        final var dive =
                diveService.getDiveById(userService.getUserById(user.id()), id).orElseThrow();
        return ResponseEntity.ok(dive);
    }

    @Operation(summary = "Get dives by custom identifier")
    @GetMapping(path = "/search")
    public ResponseEntity<List<SimplifiedDive>> searchDives(
            @AuthenticationPrincipal final User user, @RequestParam("query") final String query) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.getDiveByCustomIdentifier(user, query));
    }

    @Operation(summary = "Get next dive number")
    @GetMapping(path = "/next")
    public ResponseEntity<Integer> nextDiveNumber(@AuthenticationPrincipal final User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.getNextDiveNumber(user));
    }

    @Operation(
            summary = "Get Readers of a dive",
            responses = {
                @ApiResponse(responseCode = "200", description = "Success"),
                @ApiResponse(
                        responseCode = "403",
                        description = "The authenticated user does not have write access")
            })
    @GetMapping(path = "/{id}/readers")
    public ResponseEntity<List<FrontendUser>> getReadersOfDive(
            @AuthenticationPrincipal final User user, @PathVariable("id") final long id) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.getReaders(user, id).stream().map(User::toFrontendModel).toList());
    }

    @Operation(
            summary = "Add Readers of a dive",
            responses = {
                @ApiResponse(responseCode = "200", description = "Success"),
                @ApiResponse(
                        responseCode = "403",
                        description = "The authenticated user does not have write access")
            })
    @PostMapping(path = "/{id}/readers")
    public ResponseEntity<List<FrontendUser>> addReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @Schema(example = "[userId1, userId2]", description = "New userIDs") @Valid @RequestBody
                    final List<Long> userIds) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.addReaders(user, diveId, userIds).stream()
                        .map(User::toFrontendModel)
                        .toList());
    }

    @DeleteMapping("/{id}/readers")
    public ResponseEntity<List<FrontendUser>> deleteReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @Schema(example = "[userId1, userId2]", description = "userIDs to delete")
                    @Valid
                    @RequestBody
                    final List<Long> userIds) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.removeReaders(user, diveId, userIds).stream()
                        .map(User::toFrontendModel)
                        .toList());
    }

    @Operation(
            summary = "Add a group as reader to a dive",
            responses = {
                @ApiResponse(responseCode = "200", description = "Success"),
                @ApiResponse(
                        responseCode = "403",
                        description = "The authenticated user does not have write access")
            })
    @PostMapping(path = "/{id}/group-readers")
    public ResponseEntity<List<FrontendUser>> addGroupReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestBody final long groupId) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.addGroupReader(user, diveId, groupId).stream()
                        .map(User::toFrontendModel)
                        .toList());
    }

    @DeleteMapping("/{id}/group-readers")
    public ResponseEntity<List<FrontendUser>> deleteGroupReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestBody final long groupId) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(
                diveService.removeGroupReader(user, diveId, groupId).stream()
                        .map(User::toFrontendModel)
                        .toList());
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

    @Operation(summary = "Update a Dive, interface subject to change!!")
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
