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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.dataformat.xml.XmlMapper;

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

    // Pre-existing behavior: a well-formed UDDF file always has a <profiledata> element, so this
    // assumes Jackson populated it; a genuinely malformed file surfaces as an NPE bubbling out of
    // importUddf(...) rather than a clean validation error (this predates the NullAway rollout,
    // not addressed here).
    @SuppressWarnings("NullAway")
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

    @SuppressWarnings("NullAway")
    private UploadDiveResultStreaming importUddfSafe(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final int i,
            final UddfFile file) {
        try {
            // A null profileData here would NPE and get caught below as a per-entry import
            // failure, same as any other malformed-file error.
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
            // Explicitly the 2-arg (value, exception) constructor, both null: this represents
            // "nothing to import" rather than the 1-arg @NotNull-value success constructor.
            return new DBResult<>(null, null);
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
        if (siteId == null && diveSite == null) {
            throw new MissingValueException(MissingValueField.DIVE_SITE);
        }
        final var site =
                siteId != null
                        ? diveDataService
                                .findDiveSiteById(siteId)
                                .orElseThrow(
                                        () ->
                                                new NoSuchElementException(
                                                        "Could not find DiveSite by ID " + siteId))
                        : diveDataService
                                .findDiveSiteByName(Objects.requireNonNull(diveSite))
                                .orElseThrow(
                                        () ->
                                                new MissingDiveSiteValueException(
                                                        Objects.requireNonNull(diveSite)));
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

    /**
     * Re-parses a single entry of a UDDF file and replaces the measurements of an existing dive
     * profile with the result, leaving every other property of the dive untouched. Recovery tool
     * for backfilling dives that were imported before a parser fix (e.g. missing deco stops).
     */
    public Dive reimportProfile(
            final User user,
            final long diveId,
            final long profileId,
            final int entry,
            final InputStream inputStream)
            throws IOException {
        final var file = xmlMapper.readValue(inputStream, UddfFile.class);
        if (!UddfFile.validate(file, entry)) {
            throw new IllegalArgumentException(
                    "Entry " + entry + " of the file has too few waypoints to reimport");
        }
        return diveService.reimportProfile(
                user,
                diveId,
                profileId,
                file.exportMeasurements(entry),
                file.exportStart(entry),
                file.exportEnd(entry));
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
