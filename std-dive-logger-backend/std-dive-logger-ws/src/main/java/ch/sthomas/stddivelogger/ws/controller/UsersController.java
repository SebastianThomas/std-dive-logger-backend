package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(path = "/icon", consumes = MULTIPART_FORM_DATA_VALUE)
    public FrontendUser uploadIcon(
            @AuthenticationPrincipal final User user, @RequestParam("file") final MultipartFile file) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to upload an icon.");
        }
        return userService.uploadCustomIcon(user, file).toFrontendModel();
    }

    @DeleteMapping("/icon")
    public FrontendUser resetIcon(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to reset your icon.");
        }
        return userService.resetCustomIcon(user).toFrontendModel();
    }

    @PostMapping(path = "/background", consumes = MULTIPART_FORM_DATA_VALUE)
    public FrontendUser uploadBackground(
            @AuthenticationPrincipal final User user, @RequestParam("file") final MultipartFile file) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to upload a background image.");
        }
        return userService.uploadCustomBackground(user, file).toFrontendModel();
    }

    @DeleteMapping("/background")
    public FrontendUser resetBackground(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("You must be logged in to reset your background image.");
        }
        return userService.resetCustomBackground(user).toFrontendModel();
    }
}
