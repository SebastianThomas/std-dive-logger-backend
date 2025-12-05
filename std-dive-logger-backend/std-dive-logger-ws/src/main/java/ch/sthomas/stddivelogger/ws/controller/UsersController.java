package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
public class UsersController {

    private final UserService userService;

    public UsersController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public FrontendUser loggedInUser(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to access users.");
        }
        return user.toFrontendModel();
    }

    @GetMapping("/{id}")
    public FrontendUser user(
            @AuthenticationPrincipal final User user, @PathVariable final long id) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to access users.");
        }
        return userService.getUserById(id).toFrontendModel();
    }

    @GetMapping("/search")
    public PagedResponse<FrontendUser> user(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "query") final String query,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to access users.");
        }
        return userService.getUsersByPartialName(query, page).map(User::toFrontendModel);
    }
}
