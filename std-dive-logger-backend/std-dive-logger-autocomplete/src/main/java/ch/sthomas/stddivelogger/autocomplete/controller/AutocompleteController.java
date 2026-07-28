package ch.sthomas.stddivelogger.autocomplete.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.TagService;
import ch.sthomas.stddivelogger.service.UserService;

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/autocomplete")
public class AutocompleteController {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteController.class);
    private final DiveService diveService;
    private final UserService userService;
    private final TagService tagService;

    public AutocompleteController(final DiveService diveService, final UserService userService,
                                  final TagService tagService) {
        this.diveService = diveService;
        this.userService = userService;
        this.tagService = tagService;
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

    @GetMapping("/tag")
    public List<TagDefinition> tag(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "query") final String query) {
        // The autocomplete service has no JWT filter, so user may be null.
        // Fall back to system-wide tags only in that case.
        if (user == null) {
            return tagService.getSystemTagsByPartialName(query);
        }
        return tagService.getTagsByPartialName(user, query);
    }
}
