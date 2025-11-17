package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/v1/groups")
public class GroupController {
    private final UserService userService;

    public GroupController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public GroupWithMembers group(@PathVariable final long id) {
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
        return userService.saveGroup(body.name(), Set.of(user.id()));
    }

    @PostMapping("/{id}/join")
    public GroupWithMembers groupJoin(
            @AuthenticationPrincipal final User user, @RequestBody final long groupId) {
        return userService.joinGroup(groupId, user.id());
    }
}
