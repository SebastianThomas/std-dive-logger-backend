package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterUddfReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ImportService {
    private final XmlMapper xmlMapper;
    private final DiveService diveService;
    private final DiveDataService diveDataService;
    private final FitReaderService fitReaderService;
    private final ShearwaterUddfReaderService shearwaterUddfReaderService;
    private final SubsurfaceXmlReaderService subsurfaceXmlReaderService;

    public ImportService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveDataService diveDataService,
            final FitReaderService fitReaderService,
            ShearwaterUddfReaderService shearwaterUddfReaderService,
            SubsurfaceXmlReaderService subsurfaceXmlReaderService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
        this.fitReaderService = fitReaderService;
        this.shearwaterUddfReaderService = shearwaterUddfReaderService;
        this.subsurfaceXmlReaderService = subsurfaceXmlReaderService;
    }

    public List<SimplifiedDive> uploadDive(
            final User user, final MultipartFile file, final UploadDiveBody body)
            throws IOException {
        return importFile(user, file.getOriginalFilename(), body, file.getInputStream());
    }

    List<SimplifiedDive> importFile(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        return switch (body.fileType()) {
            case NONE -> throw new IllegalArgumentException("Invalid file type " + body.fileType());
            case UDDF_SHEARWATER ->
                    List.of(
                            shearwaterUddfReaderService.importUddf(
                                    user, filename, body, inputStream));
            case FIT_GARMIN ->
                    List.of(fitReaderService.readFitAndSaveDive(user, filename, body, inputStream));
            case XML_SUBSURFACE ->
                    subsurfaceXmlReaderService.importSubsurfaceXml(
                            user, filename, body, inputStream);
        };
    }
}
