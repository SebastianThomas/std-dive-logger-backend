package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.garmin.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterDBReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterUddfReaderService;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ImportService {
    private final DiveService diveService;
    private final DiveDataService diveDataService;
    private final FitReaderService fitReaderService;
    private final ShearwaterUddfReaderService shearwaterUddfReaderService;
    private final ShearwaterDBReaderService shearwaterDBReaderService;

    public ImportService(
            final DiveService diveService,
            final DiveDataService diveDataService,
            final FitReaderService fitReaderService,
            ShearwaterUddfReaderService shearwaterUddfReaderService,
            ShearwaterDBReaderService shearwaterDBReaderService) {
        this.diveService = diveService;
        this.diveDataService = diveDataService;
        this.fitReaderService = fitReaderService;
        this.shearwaterUddfReaderService = shearwaterUddfReaderService;
        this.shearwaterDBReaderService = shearwaterDBReaderService;
    }

    public Dive importDiveFile(final User user, final MultipartFile file, final UploadDiveBody body)
            throws IOException {
        return importDiveFile(user, file.getOriginalFilename(), body, file.getInputStream());
    }

    Dive importDiveFile(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        return switch (body.fileType()) {
            case NONE -> throw new IllegalArgumentException("Invalid file type " + body.fileType());
            case UDDF ->
                    shearwaterUddfReaderService.readUddfAndSaveDive(
                            user, filename, body, inputStream);
            case DB -> shearwaterDBReaderService.importDB(user, filename, body, inputStream);
            case FIT_GARMIN ->
                    fitReaderService.readFitAndSaveDive(user, filename, body, inputStream);
        };
    }
}
