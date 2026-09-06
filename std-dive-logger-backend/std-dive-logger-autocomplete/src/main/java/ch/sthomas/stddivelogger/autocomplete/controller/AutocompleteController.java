package ch.sthomas.stddivelogger.autocomplete.controller;

import ch.sthomas.stddivelogger.autocomplete.services.AutocompleteQueries;
import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/autocomplete")
@Validated
public class AutocompleteController {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteController.class);
    private final AutocompleteQueries queries;

    public AutocompleteController(final AutocompleteQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/user")
    public PagedResponse<FrontendUser> user(
            @RequestParam(name = "query") @NotBlank final String query,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return queries.users(query, page);
    }

    @GetMapping("/site")
    public PagedResponse<DiveSite> location(
            @RequestParam(name = "query") @NotBlank final String query,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return queries.sites(query, page);
    }

    @GetMapping("/group")
    public List<Group> group(
            @RequestParam(name = "query") @NotBlank final String query,
            @RequestParam(name = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return queries.groups(query, page);
    }

    @GetMapping("/tag")
    public List<TagDefinition> tag(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "query") @NotBlank final String query) {
        return queries.tags(query, user == null ? null : user.id());
    }
}
