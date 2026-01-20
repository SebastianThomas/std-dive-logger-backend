package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/computers")
public class ComputerController {
    private final DiveService diveService;

    public ComputerController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("")
    public PagedResponse<DiveComputer> getUserDiveComputers(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access your dive computers");
        }
        return diveService.getDiveComputers(user, page);
    }

    @GetMapping("/manufacturers")
    public PagedResponse<DiveComputerManufacturer> getUserDiveComputerManufacturers(
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        return diveService.getDiveComputerManufacturers(page);
    }

    public record UpdateDiveComputerBody(String customIdentifier) {}

    @PutMapping("/{id}")
    public DiveComputer updateDiveComputer(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") final long computerId,
            @Valid @NotNull @NotBlank @RequestBody final UpdateDiveComputerBody customIdentifier) {
        return diveService.updateDiveComputer(user, computerId, customIdentifier.customIdentifier());
    }

    @GetMapping("/search")
    public PagedResponse<DiveComputer> getUserDiveComputersByName(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "query") final String query,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to search dive computers");
        }
        return diveService.getDiveComputers(user, query, page);
    }

    @DeleteMapping("/unused")
    public int deleteDiveComputers(@AuthenticationPrincipal final User user) {
        return diveService.deleteUnusedDiveComputers(user);
    }
}
