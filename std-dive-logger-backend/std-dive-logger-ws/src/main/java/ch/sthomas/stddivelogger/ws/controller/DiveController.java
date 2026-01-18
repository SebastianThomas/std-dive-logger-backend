package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.*;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.service.importer.ImportService;

import com.google.common.primitives.Longs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.hibernate.query.SortDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/v1/dives")
@Valid
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
    public PagedResponse<SimplifiedDive> getDivesForUser(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection,
            @RequestParam(name = "includeReader", defaultValue = "false")
                    final boolean includeReader) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives");
        }
        return diveService.getDivesForUser(
                userService.getUserById(user.id()),
                DiveSort.ofNullable(sortColumn, sortDirection),
                page,
                includeReader);
    }

    @GetMapping("/group/{groupId}")
    public PagedResponse<SimplifiedDive> getDivesForGroup(
            @AuthenticationPrincipal final User user,
            @PathVariable final long groupId,
            @RequestParam(value = "page", defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access dives.");
        }
        return diveService.getDivesByGroup(user, groupId, page);
    }

    @Operation(summary = "Find dives by ID")
    @GetMapping("/ids")
    public List<SimplifiedDive> findDivesById(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "ids") final List<Long> ids) {
        if (user == null) {
            throw new UnauthorizedException("Log in to find your dives.");
        }
        return diveService.getDivesByIds(user, ids);
    }

    @Operation(summary = "Get dives by custom identifier")
    @GetMapping(path = "/custom-name")
    public PagedResponse<SimplifiedDive> searchDivesByIdentifier(
            @AuthenticationPrincipal final User user,
            @RequestParam("query") final String query,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDiveByCustomIdentifier(user, query, page);
    }

    @Operation(summary = "Get dives by dive computer id")
    @GetMapping(path = "/computer")
    public PagedResponse<SimplifiedDive> getDivesByComputer(
            @AuthenticationPrincipal final User user,
            @RequestParam("computerId") final int computerId,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDivesByComputer(
                user, computerId, DiveSort.ofNullable(sortColumn, sortDirection), page);
    }

    @Operation(summary = "Get dives by suit id")
    @GetMapping(path = "/suit")
    public PagedResponse<SimplifiedDive> getDivesBySuit(
            @AuthenticationPrincipal final User user,
            @RequestParam("suitId") final int suitId,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDivesBySuit(
                user, suitId, DiveSort.ofNullable(sortColumn, sortDirection), page);
    }

    @Operation(summary = "Get dives by custom identifier")
    @GetMapping(path = "/search")
    public PagedResponse<SimplifiedDive> searchDives(
            @AuthenticationPrincipal final User user,
            @RequestParam("query") final String query,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page,
            @RequestParam(
                            name = "includeReader",
                            defaultValue = "false") // TODO: Does this even work?
                    final boolean includeReader) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.searchDives(user, query, includeReader, page);
    }

    @Operation(summary = "Get next dive number")
    @GetMapping(path = "/next")
    public int nextDiveNumber(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to get your next dive number");
        }
        return diveService.getNextDiveNumber(user);
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
    public PagedResponse<FrontendUser> getReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestParam(name = "page", required = false, defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view readers");
        }
        return diveService.getReaders(user, diveId, page).map(User::toFrontendModel);
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
    public PagedResponse<FrontendUser> addReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @Schema(example = "[userId1, userId2]", description = "New userIDs") @Valid @RequestBody
                    final List<Long> userIds) {
        if (user == null) {
            throw new UnauthorizedException("Log in to add readers");
        }
        return diveService.addReaders(user, diveId, userIds).map(User::toFrontendModel);
    }

    @DeleteMapping("/{id}/readers")
    public PagedResponse<FrontendUser> deleteReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @Schema(example = "[userId1, userId2]", description = "userIDs to delete")
                    @Valid
                    @RequestBody
                    final List<Long> userIds) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete readers");
        }
        return diveService.removeReaders(user, diveId, userIds).map(User::toFrontendModel);
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
    public PagedResponse<FrontendUser> addGroupReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestBody final long groupId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to add group readers");
        }
        return diveService.addGroupReader(user, diveId, groupId).map(User::toFrontendModel);
    }

    @GetMapping(path = "/{id}/group-readers")
    public List<Group> getGroupReadersOfDive(
            @AuthenticationPrincipal final User user, @PathVariable("id") final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view group readers");
        }
        return diveService.getGroupReaders(user, diveId);
    }

    @DeleteMapping("/{id}/group-readers")
    public PagedResponse<FrontendUser> deleteGroupReadersOfDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestParam("groupId") final long groupId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete group readers");
        }
        return diveService.removeGroupReader(user, diveId, groupId).map(User::toFrontendModel);
    }

    @Operation(summary = "Create an empty new dive")
    @PostMapping(path = "/create")
    public ResponseEntity<Dive> createDive(
            @Valid @NotNull @RequestBody final UploadDiveBody body,
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.createEmptyDive(user, body));
    }

    @Deprecated(forRemoval = true)
    @Operation(summary = "Add a dive")
    @PostMapping(path = "/upload", consumes = MULTIPART_FORM_DATA_VALUE)
    public UploadDiveResult uploadDive(
            @AuthenticationPrincipal final User user,
            @Nullable @RequestPart("uploadBody") final UploadDiveBody body,
            @RequestParam("file") final List<MultipartFile> files) {
        if (user == null) {
            throw new UnauthorizedException("Log in to upload dive");
        }
        return importService.uploadDive(user, files, body);
    }

    @Operation(summary = "Update a Dive")
    @PutMapping(path = "", consumes = APPLICATION_JSON_VALUE)
    public Dive updateDive(
            @AuthenticationPrincipal final User user,
            @NotNull @Valid @RequestBody final UpdateDiveBody dive) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to update this dive.");
        }
        return diveService.updateDive(user, dive);
    }

    @Operation(summary = "Link Buddy Dive")
    @PostMapping(path = "/{id}/link")
    public Dive linkBuddyDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestParam final long buddyDiveId) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to link a dive from a buddy");
        }
        return diveService.linkBuddyDive(user, diveId, buddyDiveId);
    }

    @Operation(summary = "Remove linked Buddy Dive")
    @DeleteMapping(path = "/{id}/link")
    public Dive unlinkBuddyDive(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long diveId,
            @RequestParam final long buddyDiveId) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to unlink a dive from a buddy");
        }
        return diveService.unlinkBuddyDive(user, diveId, buddyDiveId);
    }

    @Operation(summary = "Merge Dive Profiles")
    @PostMapping(path = "/{id}/profiles/merge", consumes = APPLICATION_JSON_VALUE)
    public Dive mergeDiveProfiles(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long baseDiveId,
            @RequestParam final long toAddDiveId,
            @RequestParam final boolean keepToAddDive) {
        if (user == null) {
            throw new UnauthorizedException("Log in to merge dives.");
        }
        return diveService.mergeProfiles(user, baseDiveId, toAddDiveId, keepToAddDive);
    }

    public record MoveProfilesRequestBody(List<Long> profileIds, long diveId) {}

    @Operation(summary = "Move Profiles between dives")
    @PostMapping(path = "/profiles/separate", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Dive> moveProfiles(
            @AuthenticationPrincipal final User user,
            @RequestBody final MoveProfilesRequestBody body) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.moveProfiles(user, body.diveId, body.profileIds()));
    }

    public record AlignProfilesBody(
            long[] profileIds, AlignType type, @Nullable Instant alignToManual) {}

    @Operation(summary = "Align two profiles")
    @PostMapping(path = "/{id}/profiles/align", consumes = APPLICATION_JSON_VALUE)
    public Dive alignProfiles(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final int diveId,
            @RequestBody final AlignProfilesBody body) {
        if (body == null || body.profileIds.length == 0) {
            throw new IllegalArgumentException();
        }
        final var profileIds = new HashSet<>(Longs.asList(body.profileIds));
        if (profileIds.size() == 1 && body.type == AlignType.MANUAL && body.alignToManual != null) {
            // Align Manual
            return diveService.alignProfilesManualToTime(
                    user, profileIds, diveId, body.alignToManual);
        }
        if (body.profileIds.length != 2) {
            throw new IllegalArgumentException(
                    "For an auto-align, exactly two profiles are required.");
        }
        return diveService.alignProfilesAuto(
                user,
                body.profileIds[0],
                profileIds.stream().filter(p -> p != body.profileIds[0]).findFirst().orElseThrow(),
                diveId,
                body.type);
    }

    @Operation(summary = "Reset Custom Alignment")
    @PostMapping("/{id}/profiles/reset")
    public Dive resetAlignedProfiles(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final int diveId,
            @RequestBody @NotNull final List<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            throw new IllegalArgumentException();
        }
        final var profileIdsSet = new HashSet<>(profileIds);
        return diveService.resetAlignedProfiles(user, diveId, profileIdsSet);
    }

    @Operation(summary = "Generate or regenerate Preview image")
    @PostMapping(path = "/{id}/preview")
    public ResponseEntity<Dive> generatePreview(
            @AuthenticationPrincipal final User user, @PathVariable("id") final long diveId) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        final var result = diveService.createSaveDivePreview(user, diveId);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get Dive by ID")
    @GetMapping(path = "/{id}")
    public Dive getDiveById(
            @AuthenticationPrincipal final User user, @PathVariable("id") final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access dives.");
        }
        return diveService.getDiveById(userService.getUserById(user.id()), diveId).orElseThrow();
    }

    @Operation(
            summary =
                    "Delete a dive, including all associated processed items (e.g., analytics, images)")
    @DeleteMapping(path = "/{id}")
    public void deleteDive(
            @AuthenticationPrincipal final User user, @PathVariable("id") final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete dives.");
        }
        diveService.deleteDiveById(user, diveId);
    }
}
