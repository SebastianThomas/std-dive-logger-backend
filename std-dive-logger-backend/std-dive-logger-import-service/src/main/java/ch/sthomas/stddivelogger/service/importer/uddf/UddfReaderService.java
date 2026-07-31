package ch.sthomas.stddivelogger.service.importer.uddf;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Gatherer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class UddfReaderService extends BaseReaderService {
    private static final Logger logger = LoggerFactory.getLogger(UddfReaderService.class);
    private final XmlMapper xmlMapper;
    private final DiveService diveService;

    public UddfReaderService(final XmlMapper xmlMapper, final DiveService diveService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
    }

    /**
     * Parses every (unique) entry of the UDDF file. Doesn't touch the dive/site tables - only the
     * dive computer is resolved eagerly (get-or-create by serial number is idempotent, so doing it
     * now rather than at commit is harmless even if the staged import is later discarded).
     */
    public Stream<ParsedImportResultStreaming> parse(
            final User user, final String filename, final InputStream inputStream)
            throws IOException {
        final var file = xmlMapper.readValue(inputStream, UddfFile.class);
        // file.profileData() is @Nullable to model exactly this case: a file with no
        // <profiledata> section at all (missing/malformed, not just empty). getEntries() assumes
        // it's present and would NPE immediately, before any per-entry error handling below even
        // starts - catch it here instead so a bad file reports cleanly rather than 500ing the
        // whole upload.
        if (file.profileData() == null) {
            return Stream.of(
                    new ParsedImportResultStreaming(
                            Stream.empty(),
                            Stream.of(
                                    "Could not import "
                                            + filename
                                            + ": the UDDF file has no <profiledata> section.")));
        }
        final var entries = file.getEntries();
        return IntStream.range(0, entries)
                .boxed()
                .gather(uniqueProfileGatherer(entries, file))
                .map(i -> parseOneSafe(user, filename, i, file));
    }

    // Pre-existing behavior: a well-formed UDDF file always has a <profiledata> element, so this
    // assumes Jackson populated it; a genuinely malformed file surfaces as an NPE bubbling out of
    // parse(...) rather than a clean validation error (this predates the NullAway rollout, not
    // addressed here).
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
    private ParsedImportResultStreaming parseOneSafe(
            final User user, final String filename, final int i, final UddfFile file) {
        try {
            // A null profileData here would NPE and get caught below as a per-entry parse
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
            final var parsed = parseOne(user, filename, i, file);
            return new ParsedImportResultStreaming(parsed.stream(), Stream.empty());
        } catch (final Exception e) {
            logger.info("Could not parse entry {} of UDDF File", i, e);
            return new ParsedImportResultStreaming(
                    Stream.empty(),
                    Stream.of(
                            "Could not import entry "
                                    + i
                                    + " of the UDDF file: "
                                    + e.getMessage()));
        }
    }

    private Optional<ParsedImport> parseOne(
            final User user, final String filename, final int entry, final UddfFile uddfFile) {
        if (!UddfFile.validate(uddfFile, entry)) {
            return Optional.empty();
        }
        final var diveNumberGuess = uddfFile.exportDiveNumber(entry).orElse(null);
        final var notes = uddfFile.exportNotes(entry);
        final var profile = getProfile(user, uddfFile, entry);
        final var visibility = uddfFile.exportVisibility(entry).orElse(Visibility.EMPTY);
        final var payload =
                new PendingImportPayload(
                        List.of(profile),
                        notes,
                        visibility,
                        uddfFile.exportGasConsumption(entry),
                        uddfFile.getConfiguration(user),
                        uddfFile.getBuddies(),
                        diveNumberGuess);
        final var start = uddfFile.exportStart(entry);
        final var end = uddfFile.exportEnd(entry);
        return Optional.of(
                new ParsedImport(
                        PendingImportSource.UDDF_SHEARWATER,
                        null,
                        filename,
                        getDiveName(filename),
                        uddfFile.exportSite(),
                        null,
                        null,
                        uddfFile.exportDiveComputerSerialNumber(),
                        start,
                        Duration.between(start, end).toSeconds(),
                        null,
                        payload));
    }

    private DiveProfileUpload getProfile(
            final User user, final UddfFile uddfFile, final int entry) {
        final var diveComputer = getOrCreateDiveComputer(user, uddfFile);
        return new DiveProfileUpload(
                diveComputer.id(),
                uddfFile.exportStart(entry),
                uddfFile.exportEnd(entry),
                uddfFile.exportMeasurements(entry));
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
