package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.*;
import ch.sthomas.stddivelogger.service.UserService;

import org.springframework.boot.actuate.health.HealthEndpointGroupsPostProcessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/v1/groups")
public class GroupController {
    private final UserService userService;
    private final HealthEndpointGroupsPostProcessor healthEndpointGroupsPostProcessor;

    public GroupController(
            final UserService userService,
            HealthEndpointGroupsPostProcessor healthEndpointGroupsPostProcessor) {
        this.userService = userService;
        this.healthEndpointGroupsPostProcessor = healthEndpointGroupsPostProcessor;
    }

    @GetMapping("/{id}")
    public Group group(@PathVariable final long id) {
        return userService.getGroupById(id).orElseThrow();
    }

    @GetMapping("/{id}/members")
    public GroupWithMembers groupMembers(@PathVariable final long id) {
        return userService.getGroupWithMembersById(id).orElseThrow();
    }

    public record GroupBody(String name, List<Long> members) {}

    @PostMapping("")
    public GroupWithMembers group(
            @AuthenticationPrincipal final User user, @RequestBody final GroupBody body) {
        if (user == null) {
            throw new UnauthorizedException("Not logged in");
        }
        return userService.saveGroup(body.name(), user);
    }

    @PostMapping("/{id}/join")
    public GroupWithMembers groupJoin(
            @AuthenticationPrincipal final User user,
            @PathVariable(name = "id") final long groupId) {
        return userService.joinGroup(groupId, user.id());
    }

    @GetMapping("/requests")
    public List<GroupRequest> getRequests(@AuthenticationPrincipal final User user) {
        return userService.getAdminGroupRequests(user);
    }

    @PutMapping("/role")
    public GroupWithMembers changeRole(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "id") final int groupId,
            @RequestParam(name = "userId") final int userId,
            @RequestParam(name = "role") final String roleString) {
        final var role = GroupRole.find(roleString);
        if (role.isEmpty()) {
            throw new IllegalArgumentException(
                    "Group Role is invalid, valid: " + Arrays.toString(GroupRole.values()));
        }
        return userService.changeRole(user, groupId, userId, role.get());
    }
}
