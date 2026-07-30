package ch.sthomas.stddivelogger.service.importer.subsurface;

import static ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile.getUntilSeparator;
import static ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile.parseUntilSpace;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;
import ch.sthomas.stddivelogger.utils.MoreGatherers;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class SubsurfaceXmlReaderService extends BaseReaderService {
    private static final Logger logger = LoggerFactory.getLogger(SubsurfaceXmlReaderService.class);
    private final XmlMapper xmlMapper;
    private final DiveService diveService;

    public SubsurfaceXmlReaderService(final XmlMapper xmlMapper, final DiveService diveService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
    }

    private record SiteGuess(String name, Location location) {}

    /**
     * Parses every dive in the file. Doesn't touch the dive/site tables - only the dive
     * computer(s) are resolved eagerly (get-or-create by serial number is idempotent, so doing it
     * now rather than at commit is harmless even if the staged import is later discarded); dive
     * sites are only ever captured as a name+location guess here.
     */
    public Stream<ParsedImportResultStreaming> parse(
            final User user, final String filename, final InputStream inputStream)
            throws IOException {
        try (inputStream) {
            final var subsurfaceFile = xmlMapper.readValue(inputStream, SubsurfaceXmlFile.class);
            final var sites =
                    subsurfaceFile.diveSites().stream()
                            .collect(
                                    Collectors.toMap(
                                            SubsurfaceXmlFile.SubsurfaceDiveSite::uuid,
                                            site -> new SiteGuess(site.name(), site.location())));
            final var computers =
                    subsurfaceFile.dives().stream()
                            .map(SubsurfaceXmlFile.SubsurfaceDive::diveComputers)
                            .flatMap(List::stream)
                            .collect(
                                    Collectors.toMap(
                                            SubsurfaceXmlFile.SubsurfaceDiveComputer::deviceid,
                                            c ->
                                                    diveService.getOrCreateDiveComputer(
                                                            user,
                                                            getUntilSeparator(c.model(), ' '),
                                                            c.deviceid(),
                                                            c.model()),
                                            (a, b) -> a));
            return IntStream.range(0, subsurfaceFile.dives().size())
                    .mapToObj(i -> Pair.of(i, subsurfaceFile.dives().get(i)))
                    .map(dive -> parseOneSafe(user, filename, dive, computers, sites));
        }
    }

    private ParsedImportResultStreaming parseOneSafe(
            final User user,
            final String filename,
            final Pair<Integer, SubsurfaceXmlFile.SubsurfaceDive> dive,
            final Map<String, DiveComputer> computers,
            final Map<String, SiteGuess> sites) {
        try {
            final var parsed =
                    parseOne(
                            user,
                            dive.getValue(),
                            computers,
                            sites,
                            getDiveName(filename) + "-" + dive.getKey());
            return new ParsedImportResultStreaming(Stream.of(parsed), Stream.empty());
        } catch (final Exception e) {
            logger.info("Could not parse subsurface XML file dive #{}", dive.getLeft(), e);
            return new ParsedImportResultStreaming(
                    Stream.empty(),
                    Stream.of("Could not import Subsurface XML file dive #" + dive.getLeft()));
        }
    }

    private ParsedImport parseOne(
            final User user,
            final SubsurfaceXmlFile.SubsurfaceDive dive,
            final Map<String, DiveComputer> computers,
            final Map<String, SiteGuess> sites,
            final String diveIdentifierGuess) {
        final var site =
                Objects.requireNonNullElseGet(
                        sites.get(dive.divesiteid()),
                        () -> {
                            throw new IllegalArgumentException(
                                    "DiveSite does not exist in given XML file: "
                                            + dive.divesiteid());
                        });
        final var profiles = getProfiles(computers, dive);
        final var buddies =
                dive.buddy().stream().flatMap(s -> Arrays.stream(s.split(","))).toList();
        final var payload =
                new PendingImportPayload(
                        profiles,
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        buddies,
                        null);
        final var start = profiles.stream().map(DiveProfileUpload::start).min(Instant::compareTo);
        final var end = profiles.stream().map(DiveProfileUpload::end).max(Instant::compareTo);
        return new ParsedImport(
                PendingImportSource.XML_SUBSURFACE,
                null,
                null,
                diveIdentifierGuess,
                site.name(),
                site.location().lat(),
                site.location().lon(),
                null,
                start.orElse(null),
                start.isPresent() && end.isPresent()
                        ? Duration.between(start.get(), end.get()).toSeconds()
                        : null,
                null,
                payload);
    }

    public List<DiveProfileUpload> getProfiles(
            final Map<String, DiveComputer> computers,
            final SubsurfaceXmlFile.SubsurfaceDive dive) {
        final var gases =
                dive.cylinders().stream().map(SubsurfaceXmlFile.SubsurfaceCylinder::toGas).toList();
        return dive.diveComputers().stream()
                .map(
                        computer ->
                                getProfile(
                                        computer,
                                        // present by construction: computers is built from the
                                        // union of every dive's computers in the file.
                                        Objects.requireNonNull(computers.get(computer.deviceid())),
                                        gases))
                .toList();
    }

    public DiveProfileUpload getProfile(
            final SubsurfaceXmlFile.SubsurfaceDiveComputer log,
            final DiveComputer computer,
            final List<Gas> gases) {
        final var gasChanges =
                log.events().stream()
                        .filter(e -> "gaschange".equals(e.name()) && e.cylinder() != null)
                        .map(
                                c ->
                                        Pair.of(
                                                c.timeToDuration(),
                                                gases.get(Integer.parseInt(c.cylinder()))))
                        .sorted(Map.Entry.comparingByKey())
                        .toList();
        return new DiveProfileUpload(
                computer.id(), log.start(), log.end(), getMeasurements(gasChanges, log));
    }

    private List<DiveMeasurement> getMeasurements(
            final List<Pair<Duration, Gas>> gasChanges,
            final SubsurfaceXmlFile.SubsurfaceDiveComputer log) {
        return log.samples().stream()
                .map(
                        s ->
                                toMeasurement(
                                        log.start(),
                                        gasChanges.stream()
                                                .collect(
                                                        MoreGatherers.lastWhile(
                                                                g ->
                                                                        s.timeToDuration()
                                                                                .minus(g.getKey())
                                                                                .isNegative())),
                                        s))
                .toList();
    }

    private DiveMeasurement toMeasurement(
            final Instant start,
            final Optional<Pair<Duration, Gas>> switchTimeGas,
            final SubsurfaceXmlFile.SubsurfaceSample sample) {
        final var temperature =
                Optional.ofNullable(sample.temp())
                        .map(
                                t ->
                                        new Temperature(
                                                parseUntilSpace(t),
                                                Temperature.TemperatureUnit.CELSIUS))
                        .orElse(null);
        // TODO: Add other properties (PO2 (Measured?), RMV, N2, O2Tox, CNS)
        return new DiveMeasurement(
                start.plus(sample.timeToDuration()),
                temperature,
                parseUntilSpace(sample.depth()),
                sample.ndlToDuration(),
                sample.toDeco(),
                switchTimeGas.map(Pair::getValue).orElse(null),
                null,
                null,
                null,
                null,
                null);
    }
}
