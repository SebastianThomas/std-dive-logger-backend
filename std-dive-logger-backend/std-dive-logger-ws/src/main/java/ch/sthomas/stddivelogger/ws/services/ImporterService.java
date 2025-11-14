package ch.sthomas.stddivelogger.ws.services;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.service.ImportService;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ImporterService {
    private final ImportService importService;

    public ImporterService(final ImportService importService) {
        this.importService = importService;
    }

    public Dive uploadDive(final MultipartFile file, final UploadDiveBody body) throws IOException {
        return importService.importFile(file.getOriginalFilename(), body, file.getInputStream());
    }
}
