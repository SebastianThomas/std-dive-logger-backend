package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.model.user.UpdateMeRequest;
import ch.sthomas.stddivegastracker.service.UserService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API.md "Me and teams". Reference slice for new controllers: thin, delegates to a service. */
@RestController
@RequestMapping("/v1/me")
public final class MeController {

    private final UserService userService;

    public MeController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal final GasUser user) {
        return userService.me(user);
    }

    @PutMapping
    public MeResponse updateMe(
            @AuthenticationPrincipal final GasUser user,
            @Valid @RequestBody final UpdateMeRequest request) {
        return userService.updateMe(user, request);
    }
}
