package ch.sthomas.stddivelogger.analytics.controller;

import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);
    private final DiveService diveService;
    private final UserService userService;

    public AnalyticsController(final DiveService diveService, final UserService userService) {
        this.diveService = diveService;
        this.userService = userService;
    }
}
