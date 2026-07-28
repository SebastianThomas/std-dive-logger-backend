package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.controller.dive.DiveSiteWithDives;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

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

    public DiveSiteController(final DiveService diveService, final UserService userService) {
        this.diveService = diveService;
        this.userService = userService;
    }

    @Operation(
            summary = "Get all DiveSites for this user",
            description = "May need to be paginated for too many dive sites",
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

    @Operation(summary = "Get DiveSite by id")
    @GetMapping(path = "/{id}")
    public DiveSite getSite(@PathVariable @Positive final long id) {
        return diveService.getSiteById(id).orElseThrow();
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

    public record CreateDiveSiteBody(
            @NotBlank String name,
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lon) {}

    @Operation(summary = "Create new DiveSite")
    @PostMapping(path = "")
    public DiveSite createDiveSite(
            @Valid @NotNull @RequestBody final CreateDiveSiteBody body,
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create a dive site");
        }
        return diveService.createDiveSite(body.name, new Location(body.lat, body.lon));
    }
}
