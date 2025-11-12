package ch.sthomas.stddivelogger.importer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/import")
public class StdDiveDataImporter {

    private static final Logger logger = LoggerFactory.getLogger(StdDiveDataImporter.class);

    public StdDiveDataImporter() {}
}
