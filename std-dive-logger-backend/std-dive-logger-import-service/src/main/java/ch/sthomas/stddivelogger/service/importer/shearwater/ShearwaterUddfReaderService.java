package ch.sthomas.stddivelogger.service.importer.shearwater;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.exception.MissingValueException;
import ch.sthomas.stddivelogger.model.exception.MissingValueField;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ShearwaterUddfReaderService extends BaseReaderService {
    private final XmlMapper xmlMapper;
    private final DiveService diveService;
    private final DiveDataService diveDataService;

    public ShearwaterUddfReaderService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveDataService diveDataService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
    }

    public SimplifiedDive importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        return importUddf(user, filename, body, xmlMapper.readValue(inputStream, UddfFile.class));
    }

    private SimplifiedDive importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final UddfFile uddfFile) {
        final var site = getDiveSiteIdForImport(body.diveSiteId(), uddfFile.exportSite());
        final var profile = getProfile(user, uddfFile);
        final var diveName = getDiveName(body, filename);
        return diveService.saveDive(
                user,
                Optional.ofNullable(body.diveNumber()).or(uddfFile::diveNumber),
                diveName,
                uddfFile.getNotes(),
                uddfFile.getVisibility().orElse(null),
                uddfFile.getGasConsumption(),
                uddfFile.getConfiguration(),
                site,
                List.of(profile),
                uddfFile.getBuddies());
    }

    private long getDiveSiteIdForImport(
            @Nullable final Long siteId, @Nullable final String diveSite) {
        final var isSiteId = siteId != null;
        if (!isSiteId && diveSite == null) {
            throw new MissingValueException(MissingValueField.DIVE_SITE);
        }
        final var site =
                isSiteId
                        ? diveDataService
                                .findDiveSiteById(siteId)
                                .orElseThrow(
                                        () ->
                                                new NoSuchElementException(
                                                        "Could not find DiveSite by ID " + siteId))
                        : diveDataService
                                .findDiveSiteByName(diveSite)
                                .orElseThrow(() -> new MissingDiveSiteValueException(diveSite));
        return site.id();
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
        return diveService
                .getDiveComputerBySerialNumber(user, manufacturer, serialNumber)
                .or(() -> diveService.getDiveComputer(user, customIdentifier))
                .orElseGet(
                        () ->
                                diveService.createDiveComputer(
                                        serialNumber, customIdentifier, manufacturer, user.id()));
    }
}
