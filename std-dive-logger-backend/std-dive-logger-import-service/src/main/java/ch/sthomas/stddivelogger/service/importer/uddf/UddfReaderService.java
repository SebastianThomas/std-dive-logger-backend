package ch.sthomas.stddivelogger.service.importer.uddf;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResultStreaming;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.exception.DBResult;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.exception.MissingValueException;
import ch.sthomas.stddivelogger.model.exception.MissingValueField;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Gatherer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class UddfReaderService extends BaseReaderService {
    private static final Logger logger = LoggerFactory.getLogger(UddfReaderService.class);
    private final XmlMapper xmlMapper;
    private final DiveService diveService;
    private final DiveDataService diveDataService;

    public UddfReaderService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveDataService diveDataService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveDataService = diveDataService;
    }

    // @Transactional
    public Stream<UploadDiveResultStreaming> importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        final var file = xmlMapper.readValue(inputStream, UddfFile.class);
        final var entries = file.getEntries();
        return IntStream.range(0, entries)
                .boxed()
                .gather(uniqueProfileGatherer(entries, file))
                .map(i -> importUddfSafe(user, filename, body, i, file));
    }

    private static Gatherer<Integer, HashMap<UddfFile.UddfProfileRepetitionGroup, Integer>, Integer>
            uniqueProfileGatherer(final int entries, final UddfFile file) {
        return Gatherer.ofSequential(
                () -> HashMap.newHashMap(entries),
                (state, i, downstream) -> {
                    final var data = file.profileData().getData(i);
                    final var index = state.putIfAbsent(data, i);
                    if (index != null) {
                        logger.debug("Skipping i = {}, already got at index {}", i, index);
                        return true;
                    }
                    downstream.push(i);
                    return true;
                });
    }

    private UploadDiveResultStreaming importUddfSafe(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final int i,
            final UddfFile file) {
        try {
            final var data = file.profileData().getData(i);
            if (!data.timeIsValid()) {
                throw new IllegalArgumentException(
                        "Dive Time for dive with ID "
                                + data.id()
                                + " (#"
                                + i
                                + " in file) is invalid. "
                                + "If you want to import it, please update the start time.");
            }
            final var result = importUddf(user, filename, body, i, file);
            if (result.isException()) {
                return new UploadDiveResultStreaming(
                        Stream.of(), Stream.of(result.dbException().externalMessage()));
            }
            return new UploadDiveResultStreaming(
                    Optional.ofNullable(result.value()).stream(), Stream.of());
        } catch (final Exception e) {
            if (e instanceof final MissingValueException mve) {
                throw mve;
            }
            logger.info("Could not import entry {} of UDDF File", i, e);
            return new UploadDiveResultStreaming(
                    Stream.of(),
                    Stream.of(
                            "Could not import entry "
                                    + i
                                    + " of the UDDF file: "
                                    + e.getMessage()));
        }
    }

    private DBResult<SimplifiedDive> importUddf(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final int entry,
            final UddfFile uddfFile) {
        if (!UddfFile.validate(uddfFile, entry)) {
            return new DBResult<>(null);
        }
        final var diveNumber =
                Optional.ofNullable(body.diveNumber())
                        .map(DiveNumber::new)
                        .or(() -> uddfFile.exportDiveNumber(entry));
        final var notes = uddfFile.exportNotes(entry);
        final var profile = getProfile(user, uddfFile, entry);
        if (diveNumber.isPresent() && diveNumber.map(DiveNumber::isFractional).orElse(false)) {
            return new DBResult<>(diveService.addProfile(user, diveNumber.get(), notes, profile));
        }

        final var site = getDiveSiteIdForImport(body.diveSiteId(), uddfFile.exportSite());
        final var visibility = uddfFile.exportVisibility(entry).orElse(null);
        final var diveName = getDiveName(body, filename);
        return diveService.saveDive(
                user,
                diveNumber.map(DiveNumber::number),
                diveName,
                notes,
                visibility,
                uddfFile.exportGasConsumption(entry),
                uddfFile.getConfiguration(user),
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

    private DiveProfileUpload getProfile(
            final User user, final UddfFile uddfFile, final int entry) {
        final var diveComputer = getOrCreateDiveComputer(user, uddfFile);
        return new DiveProfileUpload(
                diveComputer.id(),
                uddfFile.exportStart(entry),
                uddfFile.exportEnd(entry),
                getMeasurements(uddfFile, entry));
    }

    private List<DiveMeasurement> getMeasurements(final UddfFile uddfFile, final int entry) {
        return uddfFile.exportMeasurements(entry);
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
