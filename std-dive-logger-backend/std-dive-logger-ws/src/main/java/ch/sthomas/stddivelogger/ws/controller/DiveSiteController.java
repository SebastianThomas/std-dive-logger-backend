package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.DiveSiteLink;
import ch.sthomas.stddivelogger.model.dive.DiveSiteSuggestion;
import ch.sthomas.stddivelogger.model.dive.DiveSiteType;
import ch.sthomas.stddivelogger.model.dive.conditions.SiteVisibilityLog;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.DiveSiteSuggestionService;
import ch.sthomas.stddivelogger.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/dives/sites")
@Validated
public class DiveSiteController {

    private final DiveService diveService;
    private final UserService userService;
    private final DiveSiteSuggestionService diveSiteSuggestionService;

    public DiveSiteController(
            final DiveService diveService,
            final UserService userService,
            final DiveSiteSuggestionService diveSiteSuggestionService) {
        this.diveService = diveService;
        this.userService = userService;
        this.diveSiteSuggestionService = diveSiteSuggestionService;
    }

    @Operation(
            summary = "Get all DiveSites for this user",
            description =
                    "Never paginated - every site the user has (coordinates always included) comes"
                            + " back in one response. Above DiveService.SITE_LIST_LIGHTWEIGHT_THRESHOLD"
                            + " sites, each site's diveInfo is omitted (null) to keep the payload"
                            + " small; fetch it lazily per site via GET /v1/dives/sites/{id}/dives"
                            + " when needed (e.g. a map marker's popup opening). diveCount is always"
                            + " present either way.",
            parameters = {
                @Parameter(
                        name = "includeReader",
                        description =
                                "`false`, to only get own logged dives, "
                                        + "`true`, to include dives where the user only has reader privileges")
            })
    @GetMapping(path = "")
    public List<DiveSiteWithDives<DiveSite>> getAllDiveSites(
            @AuthenticationPrincipal final User user,
            @RequestParam(value = "includeReader", defaultValue = "false")
                    final boolean includeReader) {
        return diveService.getSitesByUser(user, !includeReader);
    }

    @Operation(
            summary = "Get DiveSite by id",
            description =
                    "Includes the site's links and `canEdit` (true once the requesting user has"
                            + " logged at least one dive here) - unlike getAllDiveSites, this fetches"
                            + " the full detail for a single site and is fine to call eagerly, e.g."
                            + " when opening a site detail view.")
    @GetMapping(path = "/{id}")
    public DiveSite getSite(
            @PathVariable @Positive final long id,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            return diveService.getSiteById(id).orElseThrow();
        }
        return diveService.getSiteByIdForUser(id, user).orElseThrow();
    }

    @Operation(
            summary = "Get the current user's dives at a specific site",
            description =
                    "Lazily fetches what getAllDiveSites' diveInfo omits once a user has too many"
                            + " sites for it to inline every dive at every site - see that"
                            + " endpoint's response shape.",
            parameters = {
                @Parameter(
                        name = "includeReader",
                        description =
                                "`false`, to only get own logged dives, "
                                        + "`true`, to include dives where the user only has reader privileges")
            })
    @GetMapping(path = "/{id}/dives")
    public List<BasicDiveInfo> getDivesAtSite(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long siteId,
            @RequestParam(value = "includeReader", defaultValue = "false")
                    final boolean includeReader) {
        return diveService.getDivesAtSiteForUser(user, siteId, !includeReader);
    }

    @Operation(
            summary = "The user's own visibility readings at one site (for the visibility scatter)",
            description =
                    "Every dive the user logged here that has a visibility metres value and/or"
                            + " feeling, oldest first. lastYearOnly=true limits to the past 12"
                            + " months; default false is all time.")
    @GetMapping(path = "/{id}/visibility")
    public List<SiteVisibilityLog> getSiteVisibility(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long siteId,
            @RequestParam(value = "lastYearOnly", defaultValue = "false")
                    final boolean lastYearOnly) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view a site's visibility history");
        }
        return diveService.getSiteVisibilityLogs(user, siteId, lastYearOnly);
    }

    @Operation(summary = "Find DiveSite by location")
    @GetMapping(path = "/location")
    public List<DiveSite> findDiveSiteByLocation(
            @RequestParam("lat") @DecimalMin("-90") @DecimalMax("90") final double lat,
            @RequestParam("lon") @DecimalMin("-180") @DecimalMax("180") final double lon) {
        return diveService.getSitesByLocation(new Location(lat, lon));
    }

    @Operation(summary = "Search DiveSites by name (partial, fuzzy match)")
    @GetMapping(path = "/search")
    public PagedResponse<DiveSite> searchDiveSitesByName(
            @RequestParam("query") @NotBlank final String query,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return diveService.getSiteByPartialName(query, page);
    }

    @Operation(
            summary = "The user's own dive sites, most-dived first",
            description =
                    "Backs the empty-query autocomplete suggestion (Ctrl+Space / Down arrow).")
    @GetMapping(path = "/mine")
    public List<DiveSite> myDiveSites(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "limit", defaultValue = "20") @Positive final int limit) {
        return diveService.getSitesByUser(user, true).stream()
                .sorted(
                        java.util.Comparator.comparingLong(DiveSiteWithDives<DiveSite>::diveCount)
                                .reversed())
                .limit(limit)
                .map(DiveSiteWithDives::site)
                .toList();
    }

    @Operation(
            summary = "Suggest dive sites for this diver",
            description =
                    "Scores every site with data against this diver's own history: worth a"
                            + " revisit, better visibility than its neighbours, popular lately, an"
                            + " underrated find, or a good depth match. lat/lon are optional and,"
                            + " when given, add a proximity factor scored against maxDistanceKm (a"
                            + " soft preference, not a hard cutoff - default 50km when omitted)."
                            + " Not deterministic: a little randomness picks between near-tied"
                            + " candidates, so a refresh can turn up a different mix. Can be slow -"
                            + " it's meant to be called on demand, not eagerly.")
    @GetMapping(path = "/suggestions")
    public List<DiveSiteSuggestion> suggestDiveSites(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(value = "lat", required = false) @DecimalMin("-90") @DecimalMax("90")
                    final Double lat,
            @RequestParam(value = "lon", required = false) @DecimalMin("-180") @DecimalMax("180")
                    final Double lon,
            @RequestParam(value = "maxDistanceKm", required = false) @Positive
                    final Double maxDistanceKm,
            @RequestParam(value = "limit", defaultValue = "8") @Positive @Max(20) final int limit) {
        if (user == null) {
            throw new UnauthorizedException("Log in to get dive site suggestions");
        }
        return diveSiteSuggestionService.suggest(user, lat, lon, maxDistanceKm, limit);
    }

    public record CreateDiveSiteBody(
            @NotBlank String name,
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lon,
            @NotNull WaterType waterType) {}

    @Operation(
            summary = "Create new DiveSite",
            description = "waterType is required - a dive site's water is a physical property.")
    @PostMapping(path = "")
    public DiveSite createDiveSite(
            @Valid @NotNull @RequestBody final CreateDiveSiteBody body,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create a dive site");
        }
        return diveService.createDiveSite(
                body.name, new Location(body.lat, body.lon), body.waterType);
    }

    public record UpdateDiveSiteLinkBody(
            @NotBlank @Pattern(regexp = "^https?://.+\\..+") String url,
            @Size(max = 64) String label) {}

    public record UpdateDiveSiteBody(
            @Size(max = 2000) String description,
            @Size(max = 128) String countryRegion,
            Double maxDepth,
            DiveSiteType type,
            @NotNull WaterType waterType,
            @Valid @NotNull List<UpdateDiveSiteLinkBody> links) {}

    @Operation(
            summary = "Update a DiveSite's community-editable metadata",
            description =
                    "Requires the requesting user to have logged at least one dive at this site -"
                            + " site name/coordinates are not editable here, only"
                            + " description/country/maxDepth/type/waterType/links. waterType is"
                            + " required - the site cannot be saved without a valid one.")
    @PutMapping(path = "/{id}")
    public DiveSite updateDiveSite(
            @PathVariable @Positive final long id,
            @Valid @NotNull @RequestBody final UpdateDiveSiteBody body,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to edit a dive site");
        }
        return diveService.updateDiveSite(
                user,
                id,
                body.description,
                body.countryRegion,
                body.maxDepth,
                body.type,
                body.waterType,
                body.links.stream().map(l -> new DiveSiteLink(0, l.url(), l.label())).toList());
    }

    public record SetWaterTypeBody(@NotNull WaterType waterType) {}

    @Operation(
            summary = "Set just a DiveSite's water type",
            description =
                    "Lightweight path for the \"help improve this site\" suggestions - only touches"
                            + " water type, unlike the full metadata PUT. Requires a logged dive at"
                            + " the site.")
    @PostMapping(path = "/{id}/water-type", consumes = APPLICATION_JSON_VALUE)
    public DiveSite setWaterType(
            @PathVariable @Positive final long id,
            @Valid @NotNull @RequestBody final SetWaterTypeBody body,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to edit a dive site");
        }
        return diveService.setWaterTypeForSite(user, id, body.waterType());
    }
}
