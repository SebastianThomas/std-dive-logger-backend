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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/v1/computers")
@Validated
public class ComputerController {
    private final DiveService diveService;

    public ComputerController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("")
    public PagedResponse<DiveComputer> getUserDiveComputers(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access your dive computers");
        }
        return diveService.getDiveComputers(user, page);
    }

    @GetMapping("/{id}")
    public DiveComputer getDiveComputer(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long id) {
        if (user == null) {
            throw new UnauthorizedException("Log in to access your dive computers");
        }
        return diveService
                .getDiveComputerById(user, id)
                .orElseThrow(() -> new NoSuchElementException("No dive computer " + id));
    }

    @GetMapping("/manufacturers")
    public PagedResponse<DiveComputerManufacturer> getUserDiveComputerManufacturers(
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return diveService.getDiveComputerManufacturers(page);
    }

    public record UpdateDiveComputerBody(
            @NotBlank String customIdentifier,
            // Links this computer to a CCR unit the diver already owns (or clears the link when
            // null) - see DiveService#inferConfigurationFromComputer for what this enables on
            // import.
            @Nullable Long ccrUnitId) {}

    @PutMapping("/{id}")
    public DiveComputer updateDiveComputer(
            @AuthenticationPrincipal final User user,
            @PathVariable("id") @Positive final long computerId,
            @Valid @NotNull @RequestBody final UpdateDiveComputerBody body) {
        return diveService.updateDiveComputer(
                user, computerId, body.customIdentifier(), body.ccrUnitId());
    }

    @GetMapping("/search")
    public PagedResponse<DiveComputer> getUserDiveComputersByName(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "query") @NotBlank final String query,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
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
