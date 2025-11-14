package ch.sthomas.stddivelogger.autocomplete.controller;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.service.ImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/v1/autocomplete")
public class AutocompleteController {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteController.class);
    private final ImportService importService;

    public AutocompleteController(final ImportService importService) {
        this.importService = importService;
    }
}
