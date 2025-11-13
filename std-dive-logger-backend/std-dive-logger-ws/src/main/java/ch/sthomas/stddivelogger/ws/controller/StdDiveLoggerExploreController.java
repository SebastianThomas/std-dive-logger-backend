package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.service.DiveService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/explore")
public class StdDiveLoggerExploreController {
    private static final Logger logger =
            LoggerFactory.getLogger(StdDiveLoggerExploreController.class);
    private final DiveService diveService;

    public StdDiveLoggerExploreController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("/count")
    public long countDives() {
        return diveService.getDiveCount();
    }
}
