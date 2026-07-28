package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.*;
import ch.sthomas.stddivelogger.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/groups")
@Validated
public class GroupController {
    private final UserService userService;

    public GroupController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping("")
    public PagedResponse<GroupWithRole> getGroups(
            @AuthenticationPrincipal final User user,
            @RequestParam(value = "role", required = false) final GroupRole role,
            @RequestParam(value = "exclRole", required = false) final GroupRole exclRole,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero final int page) {
        return userService.getGroups(user, role, exclRole, page);
    }

    public record GroupBody(@NotBlank String name) {}

    @PostMapping("")
    public GroupWithMembers group(
            @AuthenticationPrincipal final @Nullable User user,
            @Valid @RequestBody final GroupBody body) {
        if (user == null) {
            throw new UnauthorizedException("Not logged in");
        }
        return userService.saveGroup(body.name(), user);
    }

    @GetMapping("/{id}")
    public Group group(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        return userService.getGroupById(id).orElseThrow();
    }

    @DeleteMapping("/{group}")
    public void deleteGroup(
            @AuthenticationPrincipal final User user, @PathVariable @NotBlank final String group) {
        userService.deleteGroup(user, group);
    }

    @GetMapping("/{id}/members")
    public GroupWithMembers groupMembers(
            @AuthenticationPrincipal final User user, @PathVariable @Positive final long id) {
        return userService.getGroupWithMembersById(user, id).orElseThrow();
    }

    @PostMapping("/{id}/members")
    public void groupJoin(
            @AuthenticationPrincipal final User user,
            @PathVariable(name = "id") @NotBlank final String group) {
        userService.joinGroup(userService.getGroupByIdOrName(group), user.id());
    }

    @DeleteMapping("/{id}/members")
    public void groupLeave(
            @AuthenticationPrincipal final User user,
            @PathVariable(name = "id") @NotBlank final String group) {
        userService.leaveGroup(userService.getGroupByIdOrName(group), user.id());
    }

    @GetMapping("/requests")
    public List<GroupRequest> getRequests(@AuthenticationPrincipal final User user) {
        return userService.getAdminGroupRequests(user);
    }

    @PutMapping("/role")
    public GroupWithMembers changeRole(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "id") @Positive final int groupId,
            @RequestParam(name = "userId") @Positive final int userId,
            @RequestParam(name = "role") final GroupRole role) {
        return userService.changeRole(user, groupId, userId, role);
    }
}
