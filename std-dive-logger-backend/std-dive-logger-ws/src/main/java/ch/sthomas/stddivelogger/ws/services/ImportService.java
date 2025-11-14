package ch.sthomas.stddivelogger.ws.services;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.UserService;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.annotation.Nullable;

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
    private final UserService userService;

    public ImportService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveDataService diveDataService,
            final UserService userService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
        this.userService = userService;
    }

    public Dive uploadDive(final MultipartFile file, final UploadDiveBody body) throws IOException {
        return importFile(file.getOriginalFilename(), body, file.getInputStream());
    }

    Dive importFile(final String filename, final UploadDiveBody body, final InputStream inputStream)
            throws IOException {
        return switch (body.fileType()) {
            case UDDF ->
                    importUddf(
                            userService.getUserById(body.userId()),
                            filename,
                            body,
                            xmlMapper.readValue(inputStream, UddfFile.class));
        };
    }

    private Dive importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final UddfFile uddfFile) {
        final var site = getDiveSite(body.diveSiteId(), uddfFile.exportSite());
        final var profile = getProfile(user, uddfFile);
        return diveService.saveDive(body, site, List.of(profile));
    }

    private long getDiveSite(@Nullable final Long siteId, @Nullable final String diveSite) {
        if (siteId != null) {
            return siteId;
        }
        if (diveSite == null) {
            throw new IllegalArgumentException("DiveSite must be defined.");
        }
        return diveDataService.findDiveSiteByName(diveSite).map(DiveSite::id).orElseThrow();
    }

    private DiveProfileUpload getProfile(final User user, final UddfFile uddfFile) {
        final var diveComputer = getOrCreateDiveComputer(user, uddfFile);
        return new DiveProfileUpload(
                diveComputer.id(),
                uddfFile.exportStart(),
                uddfFile.exportEnd(),
                getMeasurements(uddfFile));
    }

    private List<DiveMeasurement> getMeasurements(final UddfFile uddfFile) {
        return uddfFile.exportMeasurements();
    }

    private DiveComputer getOrCreateDiveComputer(final User user, final UddfFile uddfFile) {
        final var serialNumber = uddfFile.exportDiveComputerSerialNumber();
        final var customIdentifier = uddfFile.exportDiveComputerName();
        final var manufacturer = uddfFile.exportDiveComputerManufacturer();
        final var existingComputer = diveService.getDiveComputer(user, customIdentifier);
        return existingComputer.orElseGet(
                () ->
                        diveService.createDiveComputer(
                                serialNumber, customIdentifier, manufacturer, user.id()));
    }
}
