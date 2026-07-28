package ch.sthomas.stddivelogger.service.importer.divesoft;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResultStreaming;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.exception.MissingDiveSiteValueException;
import ch.sthomas.stddivelogger.model.geometry.Location;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftCeilingSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDepthSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDive;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftGraphMix;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftPressureSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftTemperatureSample;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class DivesoftReaderService extends BaseReaderService {
    private static final Logger logger = LoggerFactory.getLogger(DivesoftReaderService.class);
    private static final UploadDiveBody EMPTY_BODY =
            new UploadDiveBody(null, null, null, null, null);
    private static final DateTimeFormatter START_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM d yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH);

    private final DiveService diveService;

    public DivesoftReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    public Stream<UploadDiveResultStreaming> importDivesoft(
            final User user, final DivesoftImportRequest request) {
        final var body = Optional.ofNullable(request.body()).orElse(EMPTY_BODY);
        final var dives = request.dives().stream().map(d -> d.diveAndMixes().dive()).toList();
        // Only let a missing-dive-site error propagate as-is when importing a single dive: the
        // frontend's existing retry flow re-submits with body.diveSiteId() set, which is only
        // correct when there is exactly one dive in the request.
        final var isSingleDive = dives.size() == 1;
        return dives.stream()
                .map(
                        dive ->
                                isSingleDive
                                        ? importOne(user, dive, body)
                                        : importOneSafe(user, dive, body));
    }

    private UploadDiveResultStreaming importOneSafe(
            final User user, final DivesoftDive dive, final UploadDiveBody body) {
        try {
            return importOne(user, dive, body);
        } catch (final Exception e) {
            logger.info("Could not import Divesoft dive {}", dive.id(), e);
            return new UploadDiveResultStreaming(
                    Stream.empty(),
                    Stream.of(
                            MessageFormat.format(
                                    "Could not import Divesoft dive {0}: {1}",
                                    dive.id(), e.getMessage())));
        }
    }

    private UploadDiveResultStreaming importOne(
            final User user, final DivesoftDive dive, final UploadDiveBody body) {
        final var computer = getOrCreateComputer(user, dive);
        final var site = resolveSite(body, dive);
        final var profile = getDiveProfile(computer, dive);
        final var diveName =
                Optional.ofNullable(body.diveIdentifier())
                        .or(() -> Optional.ofNullable(dive.description()).filter(s -> !s.isBlank()))
                        .orElse("Divesoft dive " + dive.id());
        final var visibility =
                dive.visibility() != null
                        ? new Visibility(dive.visibility(), "", null)
                        : Visibility.EMPTY;
        final var result =
                diveService.saveDive(
                        user,
                        Optional.ofNullable(body.diveNumber()),
                        diveName,
                        "",
                        visibility,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        site.id(),
                        List.of(profile),
                        List.of());
        if (result.isException()) {
            return new UploadDiveResultStreaming(
                    Stream.of(), Stream.of(result.dbException().externalMessage()));
        }
        return new UploadDiveResultStreaming(Stream.of(result.value()), Stream.of());
    }

    private DiveComputer getOrCreateComputer(final User user, final DivesoftDive dive) {
        final var serial = dive.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException(
                    "Divesoft dive " + dive.id() + " has no device serial number");
        }
        return diveService.getOrCreateDiveComputer(user, "Divesoft", serial, serial);
    }

    private DiveSite resolveSite(final UploadDiveBody body, final DivesoftDive dive) {
        if (body.diveSiteId() != null) {
            return diveService.getSiteById(body.diveSiteId()).orElseThrow();
        }
        if (dive.latitude() == null || dive.longitude() == null) {
            throw new MissingDiveSiteValueException(Optional.ofNullable(dive.site()).orElse(""));
        }
        final var name =
                Optional.ofNullable(dive.site())
                        .filter(s -> !s.isBlank())
                        .orElseGet(
                                () ->
                                        MessageFormat.format(
                                                "unnamed-{0}-{1}",
                                                dive.latitude(), dive.longitude()));
        return diveService.getOrCreateDiveSite(
                name, new Location(dive.latitude(), dive.longitude()));
    }

    DiveProfileUpload getDiveProfile(final DiveComputer computer, final DivesoftDive dive) {
        final var graph = dive.graphData();
        final var depthSamples =
                graph != null && graph.depth() != null
                        ? graph.depth()
                        : List.<DivesoftDepthSample>of();
        final var temperatureSamples =
                graph != null && graph.temperature() != null
                        ? graph.temperature()
                        : List.<DivesoftTemperatureSample>of();
        final var ceilingSamples =
                graph != null && graph.ceiling() != null
                        ? graph.ceiling()
                        : List.<DivesoftCeilingSample>of();
        final var setpointSamples =
                graph != null && graph.setpoint() != null
                        ? graph.setpoint()
                        : List.<DivesoftPressureSample>of();
        final var ppo2Samples =
                graph != null && graph.ppo2() != null
                        ? graph.ppo2()
                        : List.<DivesoftPressureSample>of();
        // Not guaranteed to arrive in timestamp order (observed: the initial timestamp=0 mix can
        // be the *last* entry), so sort explicitly before walking it as a timeline.
        final var mixes =
                (graph != null && graph.mixes() != null
                                ? graph.mixes().stream()
                                : Stream.<DivesoftGraphMix>empty())
                        .sorted(Comparator.comparingLong(DivesoftGraphMix::timestamp))
                        .toList();

        final var start =
                parseStartDate(
                        Objects.requireNonNull(
                                dive.startDate(),
                                "Divesoft dive " + dive.id() + " has no startDate"));
        final var duration = parseDuration(dive.duration());
        final var end = duration != null ? start.plus(duration) : start;

        final var measurements = new ArrayList<DiveMeasurement>(depthSamples.size());
        var mixIndex = 0;
        Gas currentGas = mixes.isEmpty() ? null : toGas(mixes.getFirst());
        for (var i = 0; i < depthSamples.size(); i++) {
            final var depthSample = depthSamples.get(i);
            final var timestamp = depthSample.timestamp();
            while (mixIndex + 1 < mixes.size()
                    && mixes.get(mixIndex + 1).timestamp() <= timestamp) {
                mixIndex++;
                currentGas = toGas(mixes.get(mixIndex));
            }
            final var temperature = getSampleAt(temperatureSamples, i);
            final var ceiling = getSampleAt(ceilingSamples, i);
            final var setpoint = getSampleAt(setpointSamples, i);
            final var ppo2 = getSampleAt(ppo2Samples, i);
            final var isLast = i == depthSamples.size() - 1;
            measurements.add(
                    new DiveMeasurement(
                            start.plusSeconds(timestamp),
                            temperature != null
                                    ? new Temperature(
                                            temperature.temperature(),
                                            Temperature.TemperatureUnit.CELSIUS)
                                    : null,
                            depthSample.value(),
                            null,
                            ceiling != null && ceiling.ceiling() > 0
                                    ? List.of(new DecoStop("ceiling", ceiling.ceiling(), 0))
                                    : List.of(),
                            currentGas,
                            (setpoint != null || ppo2 != null)
                                    ? new PO2(
                                            setpoint != null ? setpoint.pressureInBar() : null,
                                            ppo2 != null ? ppo2.pressureInBar() : null,
                                            null)
                                    : null,
                            null,
                            null,
                            null,
                            isLast ? dive.cns() : null));
        }
        return new DiveProfileUpload(computer.id(), start, end, measurements);
    }

    private static <T> @Nullable T getSampleAt(final List<T> samples, final int index) {
        return index < samples.size() ? samples.get(index) : null;
    }

    private static @Nullable Gas toGas(final DivesoftGraphMix mix) {
        if (mix == null) {
            return null;
        }
        final var o2 = parsePercent(mix.mixO2());
        final var he = parsePercent(mix.mixHe());
        final var n2 = 1 - o2 - he;
        return new Gas(o2, n2, he, 0.0, null, null, mix.mixType());
    }

    private static double parsePercent(@Nullable final String percent) {
        return percent == null || percent.isBlank() ? 0.0 : Double.parseDouble(percent) / 100.0;
    }

    static Instant parseStartDate(final String raw) {
        final var trimmed = raw.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
        return OffsetDateTime.parse(trimmed, START_DATE_FORMAT).toInstant();
    }

    static @Nullable Duration parseDuration(@Nullable final String duration) {
        if (duration == null || duration.isBlank()) {
            return null;
        }
        final var parts = duration.split(":");
        return Duration.ofHours(Long.parseLong(parts[0]))
                .plusMinutes(Long.parseLong(parts[1]))
                .plusSeconds(Long.parseLong(parts[2]));
    }
}
