package ch.sthomas.stddivelogger.importer.controller;

import ch.sthomas.stddivelogger.importer.service.ImportService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/v1/import")
public class StdDiveDataImporter {

    private static final Logger logger = LoggerFactory.getLogger(StdDiveDataImporter.class);
    private final ImportService importService;

    public StdDiveDataImporter(final ImportService importService) {
        this.importService = importService;
    }

    @PostMapping({"", "/"})
    public Dive uploadFile(
            @RequestPart("file") final MultipartFile file,
            @RequestPart("uploadBody") final UploadDiveBody body)
            throws IOException {
        return importService.importFile(file.getOriginalFilename(), body, file.getInputStream());
    }
}
