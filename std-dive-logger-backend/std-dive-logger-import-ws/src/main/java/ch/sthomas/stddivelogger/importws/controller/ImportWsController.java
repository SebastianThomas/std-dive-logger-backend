package ch.sthomas.stddivelogger.importws.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/import")
public class ImportWsController {

    private static final Logger logger = LoggerFactory.getLogger(ImportWsController.class);

    public ImportWsController() {}
}
