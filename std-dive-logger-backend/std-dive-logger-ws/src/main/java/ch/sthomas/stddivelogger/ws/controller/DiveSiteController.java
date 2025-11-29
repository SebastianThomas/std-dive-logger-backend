package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.locationtech.jts.geom.Coordinate;
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

    @Operation(summary = "Get DiveSite by id")
    @GetMapping(path = "/{id}")
    public DiveSite getSite(@PathVariable("id") final long id) {
        return diveService.getSiteById(id).orElseThrow();
    }

    @Operation(summary = "Find DiveSite by location")
    @GetMapping(path = "/location")
    public List<DiveSite> findDiveSiteByLocation(
            @RequestParam("lat") final double lat, @RequestParam("lon") final double lon) {
        return diveService.getSitesByLocation(new Coordinate(lon, lat));
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
