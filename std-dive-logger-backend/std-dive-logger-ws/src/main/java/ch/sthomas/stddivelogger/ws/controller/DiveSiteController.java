package ch.sthomas.stddivelogger.ws.controller;

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
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/dives/sites")
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
    public DiveSite getSite(@PathVariable final long id) {
        return diveService.getSiteById(id).orElseThrow();
    }

    @Operation(summary = "Find DiveSite by location")
    @GetMapping(path = "/location")
    public List<DiveSite> findDiveSiteByLocation(
            @RequestParam("lat") final double lat, @RequestParam("lon") final double lon) {
        return diveService.getSitesByLocation(new Location(lat, lon));
    }

    public record CreateDiveSiteBody(String name, double lat, double lon) {}

    @Operation(summary = "Create new DiveSite")
    @PostMapping(path = "")
    public DiveSite createDiveSite(
            @Valid @NotNull @RequestBody final CreateDiveSiteBody body,
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create a dive site");
        }
        return diveService.createDiveSite(body.name, new Location(body.lat, body.lon));
    }
}
