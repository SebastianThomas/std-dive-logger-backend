package ch.sthomas.stddivelogger.autocomplete.controller;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.service.DiveService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/autocomplete")
public class AutocompleteController {

    private static final Logger logger = LoggerFactory.getLogger(AutocompleteController.class);
    private final DiveService diveService;

    public AutocompleteController(final DiveService diveService) {
        this.diveService = diveService;
    }

    @GetMapping("/site")
    public List<DiveSite> location(@RequestParam(name = "name") final String locationStart) {
        return diveService.getSiteByPartialName(locationStart);
    }
}
