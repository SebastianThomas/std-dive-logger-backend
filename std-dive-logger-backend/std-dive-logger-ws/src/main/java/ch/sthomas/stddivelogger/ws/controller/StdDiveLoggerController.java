package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import io.swagger.v3.oas.annotations.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/dives")
public class StdDiveLoggerController {

    private static final Logger logger = LoggerFactory.getLogger(StdDiveLoggerController.class);

    private final DiveService diveService;
    private final UserService userService;

    public StdDiveLoggerController(final DiveService diveService, UserService userService) {
        this.diveService = diveService;
        this.userService = userService;
    }

    // TODO: Remove later, replace by get dives for logged in user
    @Operation(summary = "Get Dives for User")
    @GetMapping(path = "")
    public List<Dive> getDivesForUser(@RequestParam final int userId) {
        return diveService.getDivesForUser(userService.getUserById(userId));
    }

    @Operation(summary = "Add a dive")
    @PostMapping(path = "")
    public Dive uploadDive(@RequestBody final UploadDiveBody body) {
        return diveService.saveDive(body);
    }
}
