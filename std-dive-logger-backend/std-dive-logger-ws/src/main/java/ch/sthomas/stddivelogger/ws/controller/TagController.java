package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.TagService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/tags")
@Valid
public class TagController {

    private final TagService tagService;

    public TagController(final TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "List all tag definitions visible to the current user (system defaults + own tags)")
    @GetMapping
    public List<TagDefinition> getTags(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view tags.");
        }
        return tagService.getTagsForUser(user);
    }

    @Operation(summary = "Create a new user-defined tag")
    @PostMapping
    public TagDefinition createTag(
            @AuthenticationPrincipal final User user,
            @NotNull @NotBlank @RequestParam final String name) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create tags.");
        }
        return tagService.createTag(user, name);
    }
}
