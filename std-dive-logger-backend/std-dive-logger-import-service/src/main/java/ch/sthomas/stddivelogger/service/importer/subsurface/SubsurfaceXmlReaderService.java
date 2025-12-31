package ch.sthomas.stddivelogger.service.importer.subsurface;

import static ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile.getUntilSeparator;
import static ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile.parseUntilSpace;

import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.utils.MoreGatherers;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class SubsurfaceXmlReaderService extends BaseReaderService {
    private final XmlMapper xmlMapper;
    private final DiveService diveService;
    private final DiveSiteRepository diveSiteRepository;

    public SubsurfaceXmlReaderService(
            final XmlMapper xmlMapper,
            final DiveService diveService,
            final DiveSiteRepository diveSiteRepository) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
        this.diveSiteRepository = diveSiteRepository;
    }

    public List<SimplifiedDive> importSubsurfaceXml(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        try (inputStream) {
            final var subsurfaceFile = xmlMapper.readValue(inputStream, SubsurfaceXmlFile.class);
            final var sites =
                    subsurfaceFile.diveSites().stream()
                            .map(site -> Map.entry(site.uuid(), findOrCreateDiveSite(site)))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            final var computers =
                    subsurfaceFile.dives().stream()
                            .map(SubsurfaceXmlFile.SubsurfaceDive::diveComputers)
                            .flatMap(List::stream)
                            .gather(MoreGatherers.distinctBy(this::computerId))
                            .map(
                                    c ->
                                            Map.entry(
                                                    c.deviceid(),
                                                    diveService.getOrCreateDiveComputer(
                                                            user,
                                                            getUntilSeparator(c.model(), ' '),
                                                            c.deviceid(),
                                                            c.model())))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return IntStream.range(0, subsurfaceFile.dives().size())
                    .mapToObj(i -> Pair.of(i, subsurfaceFile.dives().get(i)))
                    .map(
                            dive ->
                                    importSubsurfaceXmlDive(
                                            user,
                                            body,
                                            dive.getValue(),
                                            computers,
                                            sites,
                                            getDiveName(body, filename) + "-" + dive.getKey()))
                    .toList();
        }
    }

    private Pair<String, String> computerId(
            final SubsurfaceXmlFile.SubsurfaceDiveComputer computer) {
        return Pair.of(computer.model(), computer.deviceid());
    }

    private SimplifiedDive importSubsurfaceXmlDive(
            final User user,
            final UploadDiveBody body,
            final SubsurfaceXmlFile.SubsurfaceDive dive,
            final Map<String, DiveComputer> computers,
            final Map<String, DiveSite> sites,
            final String diveName) {
        final var site =
                Objects.requireNonNullElseGet(
                        sites.get(dive.divesiteid()),
                        () -> {
                            throw new IllegalArgumentException(
                                    "DiveSite does not exist in given XML file: "
                                            + dive.divesiteid());
                        });
        final var profile = getProfiles(computers, dive);
        final var buddies =
                dive.buddy().stream().flatMap(s -> Arrays.stream(s.split(","))).toList();
        return diveService.saveDive(
                user,
                Optional.ofNullable(body.diveNumber()),
                diveName,
                "",
                null,
                null,
                null,
                site.id(),
                profile,
                buddies);
    }

    public List<DiveProfileUpload> getProfiles(
            final Map<String, DiveComputer> computers,
            final SubsurfaceXmlFile.SubsurfaceDive dive) {
        final var gases =
                dive.cylinders().stream().map(SubsurfaceXmlFile.SubsurfaceCylinder::toGas).toList();
        return dive.diveComputers().stream()
                .map(computer -> getProfile(computer, computers.get(computer.deviceid()), gases))
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
        // TODO: Add other properties (RMV, N2, O2Tox, CNS)
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
                null);
    }

    private DiveSite findOrCreateDiveSite(final SubsurfaceXmlFile.SubsurfaceDiveSite site) {
        return diveService.getOrCreateDiveSite(site.name(), site.location());
    }
}
