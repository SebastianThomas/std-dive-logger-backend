package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/dives/configuration")
@Valid
public class ConfigurationController {
    private final DiveService diveService;

    public ConfigurationController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("/suit")
    public PagedResponse<Suit> getSuits(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        return diveService.getSuits(user, page);
    }

    @GetMapping("/suit/{id}")
    public Suit getSuit(
            @AuthenticationPrincipal @NotNull final User user, @PathVariable final long id) {
        return diveService.getSuitById(user, id);
    }

    @PostMapping("/suit")
    public Suit createSuit(
            @AuthenticationPrincipal @NotNull final User user,
            final @NotNull SuitType type,
            @Nullable final Double thickness,
            @Nullable final String notes) {
        return diveService.createSuit(user, type, thickness, notes);
    }

    @PutMapping("/suit/{id}")
    public Suit updateSuit(
            @AuthenticationPrincipal @NotNull final User user,
            @PathVariable final long id,
            @RequestBody @Valid final Suit suit) {
        return diveService.updateSuit(user, id, suit);
    }
}
