package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.ws.services.ImporterService;

import io.swagger.v3.oas.annotations.Operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/v1/dives")
public class StdDiveLoggerController {

    private static final Logger logger = LoggerFactory.getLogger(StdDiveLoggerController.class);

    private final DiveService diveService;
    private final UserService userService;
    private final ImporterService importerService;

    public StdDiveLoggerController(
            final DiveService diveService,
            final UserService userService,
            final ImporterService importerService) {
        this.diveService = diveService;
        this.userService = userService;
        this.importerService = importerService;
    }

    @Operation(summary = "Get Dives for User")
    @GetMapping(path = "")
    public List<Dive> getDivesForUser(@AuthenticationPrincipal final User user) {
        return diveService.getDivesForUser(userService.getUserById(user.id()));
    }

    @Operation(summary = "Add a dive")
    @PostMapping(path = "", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dive> uploadDive(
            @RequestPart("file") final MultipartFile file,
            @RequestPart("uploadBody") final UploadDiveBody body,
            @AuthenticationPrincipal final User user)
            throws IOException {
        if (user == null || user.id() != body.userId()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return ResponseEntity.ok(importerService.uploadDive(file, body));
    }
}
