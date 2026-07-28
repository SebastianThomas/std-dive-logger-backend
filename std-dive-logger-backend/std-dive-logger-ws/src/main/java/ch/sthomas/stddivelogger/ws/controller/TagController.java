package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.TagService;

import io.swagger.v3.oas.annotations.Operation;

import org.jspecify.annotations.Nullable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/v1/tags")
@Valid
public class TagController {

    private final TagService tagService;

    public TagController(final TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "List all tag definitions visible to the current user (system defaults + own tags), sorted by usage count DESC. Pass ?query= to filter by name.")
    @GetMapping
    public List<TagDefinition> getTags(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(required = false) final String query) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view tags.");
        }
        if (query != null && !query.isBlank()) {
            return tagService.getTagsByPartialName(user, query.trim());
        }
        return tagService.getTagsForUser(user);
    }

    @Operation(summary = "Create a new user-defined tag")
    @PostMapping
    public TagDefinition createTag(
            @AuthenticationPrincipal final @Nullable User user,
            @NotNull @NotBlank @RequestParam final String name) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create tags.");
        }
        return tagService.createTag(user, name);
    }

    @Operation(summary = "Delete a user-owned tag (system/auto-detect tags cannot be deleted)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable final long id) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete tags.");
        }
        try {
            tagService.deleteTag(user, id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
