package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.*;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.controller.TrimProfileBody;
import ch.sthomas.stddivelogger.model.controller.UpdateDiveBody;
import ch.sthomas.stddivelogger.model.controller.UpdateTagsBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.hibernate.query.SortDirection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/v1/dives")
@Validated
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
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
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
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable @Positive final long groupId,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access dives.");
        }
        return diveService.getDivesByGroup(user, groupId, page);
    }

    @Operation(summary = "Find dives by ID")
    @GetMapping("/ids")
    public List<SimplifiedDive> findDivesById(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "ids") @NotEmpty final List<Long> ids) {
        if (user == null) {
            throw new UnauthorizedException("Log in to find your dives.");
        }
        return diveService.getDivesByIds(user, ids);
    }

    @Operation(summary = "Get dives by custom identifier")
    @GetMapping(path = "/custom-name")
    public PagedResponse<SimplifiedDive> searchDivesByIdentifier(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("query") @NotBlank final String query,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDiveByCustomIdentifier(user, query, page);
    }

    @Operation(summary = "Get dives by dive computer id")
    @GetMapping(path = "/computer")
    public PagedResponse<SimplifiedDive> getDivesByComputer(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("computerId") @Positive final int computerId,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
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

    @Operation(summary = "Get dives by tag id")
    @GetMapping(path = "/tag")
    public PagedResponse<SimplifiedDive> getDivesByTag(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("tagId") @Positive final long tagId,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDivesByTag(
                user, tagId, DiveSort.ofNullable(sortColumn, sortDirection), page);
    }

    @Operation(
            summary =
                    "Get dives that have ALL of the given tag IDs (AND filter, repeatable ?tagIds=1&tagIds=2)")
    @GetMapping(path = "/tags")
    public PagedResponse<SimplifiedDive> getDivesByTags(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("tagIds") @NotEmpty final List<Long> tagIds,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDivesByTags(
                user, tagIds, DiveSort.ofNullable(sortColumn, sortDirection), page);
    }

    @Operation(summary = "Get dives by suit id")
    @GetMapping(path = "/suit")
    public PagedResponse<SimplifiedDive> getDivesBySuit(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("suitId") @Positive final int suitId,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
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

    @Operation(
            summary =
                    "Get dives matching any combination of filters (tags, site, suit, base"
                            + " configuration, text query, dive-start date range), ANDed together")
    @GetMapping(path = "/filtered")
    public PagedResponse<SimplifiedDive> getFilteredDives(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "tagIds", required = false) @Nullable final List<Long> tagIds,
            @RequestParam(name = "diveSiteId", required = false) @Positive @Nullable
                    final Long diveSiteId,
            @RequestParam(name = "suitId", required = false) @Positive @Nullable final Long suitId,
            @RequestParam(name = "ccrUnitId", required = false) @Positive @Nullable
                    final Long ccrUnitId,
            @RequestParam(name = "baseConfiguration", required = false) @Nullable
                    final BaseConfiguration baseConfiguration,
            @RequestParam(name = "query", required = false) @Nullable final String query,
            @RequestParam(name = "startDate", required = false) @Nullable final Instant startDate,
            @RequestParam(name = "endDate", required = false) @Nullable final Instant endDate,
            @RequestParam(name = "startTime", required = false) @Nullable final LocalTime startTime,
            @RequestParam(name = "endTime", required = false) @Nullable final LocalTime endTime,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getFilteredDives(
                user,
                new DiveFilterParams(
                        tagIds,
                        diveSiteId,
                        suitId,
                        ccrUnitId,
                        baseConfiguration,
                        query,
                        startDate,
                        endDate,
                        startTime,
                        endTime),
                DiveSort.ofNullable(sortColumn, sortDirection),
                page);
    }

    @Operation(summary = "Get dives by CCR unit id")
    @GetMapping(path = "/ccrUnit")
    public PagedResponse<SimplifiedDive> getDivesByCcrUnit(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("ccrUnitId") @Positive final long ccrUnitId,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
            @RequestParam(name = "sortCol", required = false) @Nullable
                    final DiveSortColumn sortColumn,
            @RequestParam(name = "sortDirection", required = false) @Nullable
                    final SortDirection sortDirection) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.getDivesByCcrUnit(
                user, ccrUnitId, DiveSort.ofNullable(sortColumn, sortDirection), page);
    }

    @Operation(
            summary = "Get dives by custom identifier",
            description =
                    "includeReader is currently a no-op: the underlying search already includes"
                            + " every dive the user can read (owned, buddy, explicitly-shared, and"
                            + " group-shared), regardless of this flag - see"
                            + " DiveService#searchDives.")
    @GetMapping(path = "/search")
    public PagedResponse<SimplifiedDive> searchDives(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam("query") @NotBlank final String query,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page,
            @RequestParam(name = "includeReader", defaultValue = "false")
                    final boolean includeReader) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dives.");
        }
        return diveService.searchDives(user, query, includeReader, page);
    }

    @Operation(summary = "Get next dive number")
    @GetMapping(path = "/next")
    public int nextDiveNumber(@AuthenticationPrincipal final @Nullable User user) {
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
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @RequestParam(name = "page", required = false, defaultValue = "0") @PositiveOrZero
                    final int page) {
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
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @Schema(example = "[userId1, userId2]", description = "New userIDs")
                    @NotEmpty
                    @RequestBody
                    final List<Long> userIds) {
        if (user == null) {
            throw new UnauthorizedException("Log in to add readers");
        }
        return diveService.addReaders(user, diveId, userIds).map(User::toFrontendModel);
    }

    @DeleteMapping("/{id}/readers")
    public PagedResponse<FrontendUser> deleteReadersOfDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @Schema(example = "[userId1, userId2]", description = "userIDs to delete")
                    @NotEmpty
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
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @RequestBody @Positive final long groupId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to add group readers");
        }
        return diveService.addGroupReader(user, diveId, groupId).map(User::toFrontendModel);
    }

    @GetMapping(path = "/{id}/group-readers")
    public List<Group> getGroupReadersOfDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view group readers");
        }
        return diveService.getGroupReaders(user, diveId);
    }

    @DeleteMapping("/{id}/group-readers")
    public PagedResponse<FrontendUser> deleteGroupReadersOfDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @RequestParam("groupId") @Positive final long groupId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete group readers");
        }
        return diveService.removeGroupReader(user, diveId, groupId).map(User::toFrontendModel);
    }

    @Operation(summary = "Create an empty new dive")
    @PostMapping(path = "/create")
    public ResponseEntity<Dive> createDive(
            @Valid @NotNull @RequestBody final UploadDiveBody body,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.createEmptyDive(user, body));
    }

    @Operation(summary = "Update a Dive")
    @PutMapping(path = "", consumes = APPLICATION_JSON_VALUE)
    public Dive updateDive(
            @AuthenticationPrincipal final @Nullable User user,
            @NotNull @Valid @RequestBody final UpdateDiveBody dive) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to update this dive.");
        }
        return diveService.updateDive(user, dive);
    }

    public record BulkUpdateDiveBody<T>(@NotNull T newValue, @NotEmpty List<Long> diveIds) {}

    @Operation(summary = "Update the base configuration of multiple dives")
    @PutMapping(path = "/base-configuration")
    public void updateBaseConfiguration(
            @AuthenticationPrincipal final User user,
            @NotNull @Valid @RequestBody final BulkUpdateDiveBody<BaseConfiguration> body) {
        diveService.updateBaseConfigurationDives(user, body.diveIds, body.newValue);
    }

    @Operation(summary = "Update the base configuration of multiple dives")
    @PutMapping(path = "/suit")
    public void updateSuit(
            @AuthenticationPrincipal final User user,
            @NotNull @Valid @RequestBody final BulkUpdateDiveBody<Long> body) {
        diveService.setSuit(user, body.diveIds, body.newValue);
    }

    @Operation(
            summary =
                    "Set the CCR unit on multiple dives at once. Any dive in the request that"
                            + " isn't itself CCR-configured is left untouched rather than"
                            + " rejecting the whole batch.")
    @PutMapping(path = "/ccrUnit")
    public void updateCcrUnit(
            @AuthenticationPrincipal final User user,
            @NotNull @Valid @RequestBody final BulkUpdateDiveBody<Long> body) {
        diveService.setCcrUnit(user, body.diveIds, body.newValue);
    }

    @Operation(summary = "Update the base configuration of multiple dives")
    @PutMapping(path = "/weight")
    public void updateWeight(
            @AuthenticationPrincipal final User user,
            @NotNull @Valid @RequestBody final BulkUpdateDiveBody<Double> body) {
        diveService.updateWeightDives(user, body.diveIds, body.newValue);
    }

    @Operation(summary = "Link Buddy Dive")
    @PostMapping(path = "/{id}/link")
    public Dive linkBuddyDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @RequestParam @Positive final long buddyDiveId) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to link a dive from a buddy");
        }
        return diveService.linkBuddyDive(user, diveId, buddyDiveId);
    }

    @Operation(summary = "Remove linked Buddy Dive")
    @DeleteMapping(path = "/{id}/link")
    public Dive unlinkBuddyDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @RequestParam @Positive final long buddyDiveId) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to unlink a dive from a buddy");
        }
        return diveService.unlinkBuddyDive(user, diveId, buddyDiveId);
    }

    public record BuddyRoleBody(@Nullable BuddyRole role) {}

    @Operation(
            summary =
                    "Set a linked buddy dive's role, as rated from this dive's own side of the link")
    @PutMapping(path = "/{id}/link/{buddyDiveId}/role")
    public Dive setBuddyDiveRole(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("buddyDiveId") @Positive final long buddyDiveId,
            @Valid @NotNull @RequestBody final BuddyRoleBody body) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to set a buddy dive's role");
        }
        return diveService.setBuddyDiveRole(user, diveId, buddyDiveId, body.role());
    }

    @Operation(
            summary =
                    "Autocomplete buddy names from the user's own dive history, ordered by frequency")
    @GetMapping("/buddies/autocomplete")
    public List<String> autocompleteBuddies(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam @NotBlank final String query) {
        if (user == null) {
            throw new UnauthorizedException("Log in to use buddy autocomplete.");
        }
        return diveService.getBuddyNameSuggestions(user, query);
    }

    @Operation(summary = "List all named dive buddies used across your dives, ordered by frequency")
    @GetMapping("/buddies")
    public List<String> getBuddyNames(@AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your buddies.");
        }
        return diveService.getAllBuddyNames(user);
    }

    public record RenameBuddyBody(@NotBlank String oldName, @NotBlank String newName) {}

    @Operation(
            summary =
                    "Rename a named dive buddy — applies to every dive of the current user that lists"
                            + " this buddy")
    @PutMapping(path = "/buddies/rename", consumes = APPLICATION_JSON_VALUE)
    public List<String> renameBuddy(
            @AuthenticationPrincipal final @Nullable User user,
            @Valid @RequestBody final RenameBuddyBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to rename a buddy.");
        }
        return diveService.renameBuddyName(user, body.oldName(), body.newName());
    }

    @Operation(
            summary =
                    "Set a named dive buddy's role — applies to every dive of the current user"
                            + " that lists this buddy")
    @PutMapping(path = "/buddies/{name}/role", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setNamedBuddyRole(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("name") @NotBlank final String name,
            @Valid @NotNull @RequestBody final BuddyRoleBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to set a buddy's role.");
        }
        diveService.setNamedBuddyRole(user, name, body.role());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary =
                    "Set a linked buddy user's role, as rated from the current user's own side —"
                            + " applies to every dive pair linked with this buddy")
    @PutMapping(path = "/buddies/users/{buddyUserId}/role", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setLinkedBuddyRoleForUser(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("buddyUserId") @Positive final long buddyUserId,
            @Valid @NotNull @RequestBody final BuddyRoleBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to set a buddy's role.");
        }
        diveService.setLinkedBuddyRoleForUser(user, buddyUserId, body.role());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary =
                    "List every user linked as a buddy on at least one of the current user's own"
                            + " dives - powers the bulk role-set picker for linked buddies")
    @GetMapping("/buddies/users")
    public List<FrontendUser> getLinkedBuddyUsers(
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view linked buddies.");
        }
        return diveService.getLinkedBuddyUsers(user).stream().map(User::toFrontendModel).toList();
    }

    @Operation(
            summary = "The user's most recent explicit buddy/team terminology choice",
            description =
                    "Powers the frontend's terminology-picker smart default - null if the user has"
                            + " never explicitly set one on any dive.")
    @GetMapping("/team-terminology/default")
    public @Nullable TeamTerminology getMostRecentTeamTerminology(
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your terminology default.");
        }
        return diveService.getMostRecentTeamTerminology(user).orElse(null);
    }

    @Operation(
            summary = "The user's dive backfill queue",
            description =
                    "Every dive still missing at least one backfill checklist item (visibility,"
                            + " gas consumption, water type, leader, notes), most"
                            + " incomplete/oldest first - powers the guided backfill flow.")
    @GetMapping("/backfill")
    public List<DiveBackfillStatus> getBackfillQueue(
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your backfill queue.");
        }
        return diveService.getBackfillQueue(user);
    }

    @Operation(summary = "Merge Dive Profiles")
    @PostMapping(path = "/{id}/profiles/merge", consumes = APPLICATION_JSON_VALUE)
    public Dive mergeDiveProfiles(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long baseDiveId,
            @RequestParam @Positive final long toAddDiveId,
            @RequestParam final boolean keepToAddDive) {
        if (user == null) {
            throw new UnauthorizedException("Log in to merge dives.");
        }
        return diveService.mergeProfiles(user, baseDiveId, toAddDiveId, keepToAddDive);
    }

    public record MoveProfilesRequestBody(@NotEmpty List<Long> profileIds, @Positive long diveId) {}

    @Operation(summary = "Move Profiles between dives")
    @PostMapping(path = "/profiles/separate", consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<Dive> moveProfiles(
            @AuthenticationPrincipal final @Nullable User user,
            @Valid @RequestBody final MoveProfilesRequestBody body) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(diveService.moveProfiles(user, body.diveId, body.profileIds()));
    }

    public record AlignProfilesBody(
            @NotEmpty long[] profileIds,
            @NotNull AlignType type,
            @Nullable Instant alignToManual) {}

    @Operation(summary = "Align two profiles")
    @PostMapping(path = "/{id}/profiles/align", consumes = APPLICATION_JSON_VALUE)
    public Dive alignProfiles(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final int diveId,
            @Valid @RequestBody final AlignProfilesBody body) {
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
            @PathVariable("id") @Positive final int diveId,
            @RequestBody @NotEmpty final List<Long> profileIds) {
        final var profileIdsSet = new HashSet<>(profileIds);
        return diveService.resetAlignedProfiles(user, diveId, profileIdsSet);
    }

    @Operation(
            summary =
                    "Delete a single profile from a dive (measurements, segments, history included)"
                            + " without deleting the dive itself - recovery path for a profile"
                            + " attached/merged to the wrong dive by mistake. Refuses to delete a"
                            + " dive's only remaining profile; delete the dive instead in that case.")
    @DeleteMapping(path = "/{id}/profiles/{profileId}")
    public Dive deleteProfile(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("profileId") @Positive final long profileId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete a profile.");
        }
        return diveService.deleteProfile(user, diveId, profileId);
    }

    @Operation(
            summary =
                    "Permanently deletes every measurement of a profile outside [trimStart, trimEnd] -"
                            + " e.g. the trailing few minutes at 0.3-0.6m a Divesoft Liberty logs while"
                            + " waiting to have its dive ended manually on the computer. Either bound may"
                            + " be omitted to only trim the other end.")
    @PostMapping(path = "/{id}/profiles/{profileId}/trim", consumes = APPLICATION_JSON_VALUE)
    public Dive trimProfile(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("profileId") @Positive final long profileId,
            @NotNull @Valid @RequestBody final TrimProfileBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to trim a profile.");
        }
        return diveService.trimProfile(user, diveId, profileId, body.trimStart(), body.trimEnd());
    }

    @Operation(
            summary =
                    "Reimport a profile's raw measurements from its original source file, leaving every"
                            + " other dive property (suit, gas consumption, weight, visibility, notes, tags,"
                            + " buddies, ...) untouched. Recovery tool for fixing importer bugs after the fact;"
                            + " currently only supports UDDF files.")
    @PostMapping(path = "/{id}/profiles/{profileId}/reimport", consumes = MULTIPART_FORM_DATA_VALUE)
    public Dive reimportProfile(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @PathVariable("profileId") @Positive final long profileId,
            @RequestParam(value = "entry", defaultValue = "0") @PositiveOrZero final int entry,
            @RequestParam("file") @NotNull final MultipartFile file) {
        if (user == null) {
            throw new UnauthorizedException("Log in to reimport a profile.");
        }
        return importService.reimportProfile(user, diveId, profileId, entry, file);
    }

    @Operation(
            summary =
                    "Set manual tags on a dive — body is a list of tag-definition IDs (replaces all existing manual tags)")
    @PutMapping(path = "/{id}/tags", consumes = APPLICATION_JSON_VALUE)
    public Dive updateTags(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId,
            @NotNull @Valid @RequestBody final UpdateTagsBody body) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to update tags.");
        }
        return diveService.updateTags(
                user, diveId, body.manualTagIds(), body.dismissedAutoTagIds());
    }

    @Operation(
            summary =
                    "Refresh auto-detected tags and return the updated dive. Call this when opening the edit page.")
    @PostMapping(path = "/{id}/refresh-tags")
    public Dive refreshAutoTags(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Please log in to refresh tags.");
        }
        return diveService.refreshAutoTags(user, diveId);
    }

    @Operation(summary = "Generate or regenerate Preview image")
    @PostMapping(path = "/{id}/preview")
    public ResponseEntity<Dive> generatePreview(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
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
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access dives.");
        }
        return diveService.getDiveById(userService.getUserById(user.id()), diveId).orElseThrow();
    }

    @Operation(
            summary = "Get the previous/next dive by number in the authenticated user's own log",
            description =
                    "Scoped to the caller's own dives only - not meaningful for a shared/reader"
                            + " view of someone else's dive, since dive numbers are only unique"
                            + " per user.")
    @GetMapping(path = "/{id}/adjacent")
    public AdjacentDives getAdjacentDives(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access dives.");
        }
        return diveService.getAdjacentDives(user, diveId).orElseThrow();
    }

    @Operation(
            summary =
                    "Delete a dive, including all associated processed items (e.g., analytics, images)")
    @DeleteMapping(path = "/{id}")
    public void deleteDive(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete dives.");
        }
        diveService.deleteDiveById(user, diveId);
    }
}
