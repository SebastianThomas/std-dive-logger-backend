package ch.sthomas.stddivelogger.autocomplete.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/autocomplete")
public class AutocompleteController {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteController.class);
    private final DiveService diveService;
    private final UserService userService;

    public AutocompleteController(final DiveService diveService, final UserService userService) {
        this.diveService = diveService;
        this.userService = userService;
    }

    @GetMapping("/user")
    public PagedResponse<FrontendUser> user(
            @RequestParam(name = "query") final String query,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        return userService.getUsersByPartialName(query, page).map(User::toFrontendModel);
    }

    @GetMapping("/site")
    public PagedResponse<DiveSite> location(
            @RequestParam(name = "query") final String query,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        return diveService.getSiteByPartialName(query, page);
    }

    @GetMapping("/group")
    public List<Group> group(
            @RequestParam(name = "query") final String query,
            @RequestParam(name = "page", defaultValue = "0") final int page) {
        return userService.getGroupsByPartialName(query, page);
    }
}
