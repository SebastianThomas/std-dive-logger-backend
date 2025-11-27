package ch.sthomas.stddivelogger.service.importer;

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
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ImportService {
    private final XmlMapper xmlMapper;
    private final DiveService diveService;
    private final DiveDataService diveDataService;
    private final FitReaderService fitReaderService;

    public ImportService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveDataService diveDataService,
            final FitReaderService fitReaderService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
        this.fitReaderService = fitReaderService;
    }

    public Dive uploadDive(final User user, final MultipartFile file, final UploadDiveBody body)
            throws IOException {
        return importFile(user, file.getOriginalFilename(), body, file.getInputStream());
    }

    Dive importFile(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        return switch (body.fileType()) {
            case NONE -> throw new IllegalArgumentException("Invalid file type " + body.fileType());
            case UDDF ->
                    importUddf(
                            user, filename, body, xmlMapper.readValue(inputStream, UddfFile.class));
            case FIT_GARMIN ->
                    fitReaderService.readFitAndSaveDive(user, filename, body, inputStream);
        };
    }

    private Dive importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final UddfFile uddfFile) {
        final var site = getDiveSiteId(body.diveSiteId(), uddfFile.exportSite());
        final var profile = getProfile(user, uddfFile);
        return diveService.saveDive(user, body, site, List.of(profile), uddfFile.getBuddies());
    }

    private Dive importFit(
            final User user, final String filename, final UploadDiveBody body, final Object fit) {
        final var site = getDiveSiteId(body.diveSiteId(), null);
        return null;
    }

    private long getDiveSiteId(@Nullable final Long siteId, @Nullable final String diveSite) {
        final var isSiteId = siteId != null;
        if (!isSiteId && diveSite == null) {
            throw new IllegalArgumentException("DiveSite must be defined.");
        }
        final var site =
                isSiteId
                        ? diveDataService.findDiveSiteById(siteId)
                        : diveDataService.findDiveSiteByName(diveSite);
        return site.map(DiveSite::id)
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        MessageFormat.format(
                                                "DiveSite not found by {0} {1}.",
                                                isSiteId ? "ID" : "Name",
                                                isSiteId ? siteId : diveSite)));
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
