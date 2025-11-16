package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.service.UserService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/groups")
public class GroupController {
    private final UserService userService;

    public GroupController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public Group group(@PathVariable final long id) {
        return userService.getGroupById(id).orElseThrow();
    }
}
