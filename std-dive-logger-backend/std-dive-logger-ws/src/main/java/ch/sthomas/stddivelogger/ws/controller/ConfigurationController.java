package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.gear.CcrUnit;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
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
@RequestMapping("/v1/dives/configuration")
@Validated
public class ConfigurationController {
    private final DiveService diveService;

    public ConfigurationController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("/suit")
    public PagedResponse<Suit> getSuits(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return diveService.getSuits(user, page);
    }

    @GetMapping("/suit/{id}")
    public Suit getSuit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id) {
        return diveService.getSuitById(user, id);
    }

    @PostMapping("/suit")
    public Suit createSuit(
            @AuthenticationPrincipal @NotNull final User user,
            final @NotNull SuitType type,
            @Nullable @Positive final Double thickness,
            @Nullable final String notes) {
        return diveService.createSuit(user, type, thickness, notes);
    }

    @PutMapping("/suit/{id}")
    public Suit updateSuit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id,
            @RequestBody @Valid final Suit suit) {
        return diveService.updateSuit(user, id, suit);
    }

    public record CreateCcrUnitBody(
            @NotBlank String name, @Nullable String notes, boolean isPublic) {}

    @GetMapping("/ccrUnit")
    public PagedResponse<CcrUnit> getCcrUnits(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return diveService.getCcrUnits(user, page);
    }

    @GetMapping("/ccrUnit/{id}")
    public CcrUnit getCcrUnit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id) {
        return diveService.getCcrUnitById(user, id);
    }

    @PostMapping("/ccrUnit")
    public CcrUnit createCcrUnit(
            @AuthenticationPrincipal @NotNull final User user,
            @RequestBody @Valid final CreateCcrUnitBody body) {
        return diveService.createCcrUnit(user, body.name(), body.notes(), body.isPublic());
    }

    @PutMapping("/ccrUnit/{id}")
    public CcrUnit updateCcrUnit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id,
            @RequestBody @Valid final CcrUnit ccrUnit) {
        return diveService.updateCcrUnit(user, id, ccrUnit);
    }

    @Operation(
            summary = "Delete a CCR unit",
            description =
                    "Unlinks the unit from every dive configuration and dive computer that"
                            + " references it, then deletes the unit itself. Never deletes a"
                            + " dive, profile, or computer.")
    @DeleteMapping("/ccrUnit/{id}")
    public void deleteCcrUnit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id) {
        diveService.deleteCcrUnit(user, id);
    }

    public record DeletedDivesResult(int deletedDives) {}

    @Operation(
            summary = "Delete a CCR unit and every dive that uses it",
            description =
                    "Permanently deletes every dive linked to this CCR unit, then the unit"
                            + " itself. Irreversible - intended only for an explicit,"
                            + " heavily-confirmed frontend action, never a casual click.")
    @DeleteMapping("/ccrUnit/{id}/dives")
    public DeletedDivesResult deleteCcrUnitAndAllDives(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable @Positive final long id) {
        return new DeletedDivesResult(diveService.deleteCcrUnitAndAllDives(user, id));
    }

    @GetMapping("/ccrUnit/autocomplete")
    public List<String> autocompleteCcrUnitNames(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam @NotBlank final String query) {
        if (user == null) {
            throw new UnauthorizedException("Log in to use CCR unit autocomplete.");
        }
        return diveService.getCcrUnitNameSuggestions(query);
    }

    @GetMapping("/ccrUnit/users")
    public List<FrontendUser> searchUsersByCcrUnit(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam @NotBlank final String query) {
        if (user == null) {
            throw new UnauthorizedException("Log in to search divers by CCR unit.");
        }
        return diveService.getUsersByPublicCcrUnitName(query);
    }
}
