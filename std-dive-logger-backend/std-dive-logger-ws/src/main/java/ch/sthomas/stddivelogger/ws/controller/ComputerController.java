package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
