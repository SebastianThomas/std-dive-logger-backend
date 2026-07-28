package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/explore")
@Validated
public class ExploreController {
    private static final Logger logger = LoggerFactory.getLogger(ExploreController.class);
    private final DiveService diveService;
    private final UserService userService;

    public ExploreController(final DiveService diveService, UserService userService) {
        this.diveService = diveService;
        this.userService = userService;
    }

    @GetMapping("/count/dives")
    public long countDives() {
        return diveService.getDiveCount();
    }

    @GetMapping("/count/users")
    public long countUsers() {
        return userService.getUserCount();
    }
}
