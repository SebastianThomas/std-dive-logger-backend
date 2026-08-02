package ch.sthomas.stddivelogger.service.importer.divesoft;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftImportRequest;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftCeilingSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDepthSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDive;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftGraphMix;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftModeSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftPressureSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftTemperatureSample;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;

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
    private static final DateTimeFormatter START_DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM d yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH);

    private final DiveService diveService;

    public DivesoftReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    /**
     * Parses every dive in the request. Doesn't touch the dive/site tables - only the dive computer
     * is resolved eagerly (get-or-create by serial number is idempotent, so doing it now rather
     * than at commit is harmless even if the staged import is later discarded).
     */
    public ParsedImportResultStreaming parse(final User user, final DivesoftImportRequest request) {
        // diveAndMixes/dive are only @Nullable to admit a malformed request at the type level -
        // @NotNull @Valid bean validation at the controller boundary already rejects a request
        // with either missing before this method ever runs (see DivesoftImportRequestTest).
        final var dives =
                request.dives().stream()
                        .map(
                                d ->
                                        Objects.requireNonNull(
                                                Objects.requireNonNull(d.diveAndMixes()).dive()))
                        .toList();
        return dives.stream()
                .map(dive -> parseOneSafe(user, dive))
                .reduce(ParsedImportResultStreaming::concat)
                .orElse(new ParsedImportResultStreaming(Stream.empty(), Stream.empty()));
    }

    private ParsedImportResultStreaming parseOneSafe(final User user, final DivesoftDive dive) {
        try {
            return new ParsedImportResultStreaming(Stream.of(parseOne(user, dive)), Stream.empty());
        } catch (final Exception e) {
            logger.info("Could not parse Divesoft dive {}", dive.id(), e);
            return new ParsedImportResultStreaming(
                    Stream.empty(),
                    Stream.of(
                            MessageFormat.format(
                                    "Could not import Divesoft dive {0}: {1}",
                                    dive.id(), e.getMessage())));
        }
    }

    private ParsedImport parseOne(final User user, final DivesoftDive dive) {
        final var computer = getOrCreateComputer(user, dive);
        final var profile = getDiveProfile(computer, dive);
        final var diveIdentifierGuess =
                Optional.ofNullable(dive.description())
                        .filter(s -> !s.isBlank())
                        .orElse("Divesoft dive " + dive.id());
        final var visibility =
                dive.visibility() != null
                        ? new Visibility(dive.visibility(), "", null)
                        : Visibility.EMPTY;
        final var hasCoordinates = dive.latitude() != null && dive.longitude() != null;
        final var siteNameGuess =
                hasCoordinates
                        ? Optional.ofNullable(dive.site())
                                .filter(s -> !s.isBlank())
                                .orElseGet(
                                        () ->
                                                MessageFormat.format(
                                                        "unnamed-{0}-{1}",
                                                        dive.latitude(), dive.longitude()))
                        : null;
        final var start =
                parseStartDate(
                        Objects.requireNonNull(
                                dive.startDate(),
                                "Divesoft dive " + dive.id() + " has no startDate"));
        final var duration = parseDuration(dive.duration());
        final var payload =
                new PendingImportPayload(
                        List.of(profile),
                        "",
                        visibility,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        List.of(),
                        null);
        return new ParsedImport(
                PendingImportSource.DIVESOFT,
                dive.id(),
                null,
                diveIdentifierGuess,
                siteNameGuess,
                hasCoordinates ? dive.latitude() : null,
                hasCoordinates ? dive.longitude() : null,
                dive.deviceSerial(),
                start,
                duration != null ? duration.toSeconds() : null,
                dive.maxDepth(),
                payload);
    }

    private DiveComputer getOrCreateComputer(final User user, final DivesoftDive dive) {
        final var serial = dive.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException(
                    "Divesoft dive " + dive.id() + " has no device serial number");
        }
        return diveService.getOrCreateDiveComputer(user, "Divesoft", serial, serial);
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
        // Same "sparse timeline of changes, hold the last value forward" shape as mixes above -
        // only present at all on CCR-capable dives, and only carries entries where the mode
        // actually changed.
        final var modes =
                (graph != null && graph.modes() != null
                                ? graph.modes().stream()
                                : Stream.<DivesoftModeSample>empty())
                        .sorted(Comparator.comparingLong(DivesoftModeSample::timestamp))
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
        var modeIndex = 0;
        DiveMode currentMode = modes.isEmpty() ? null : toMode(modes.getFirst());
        for (var i = 0; i < depthSamples.size(); i++) {
            final var depthSample = depthSamples.get(i);
            final var timestamp = depthSample.timestamp();
            while (mixIndex + 1 < mixes.size()
                    && mixes.get(mixIndex + 1).timestamp() <= timestamp) {
                mixIndex++;
                currentGas = toGas(mixes.get(mixIndex));
            }
            while (modeIndex + 1 < modes.size()
                    && modes.get(modeIndex + 1).timestamp() <= timestamp) {
                modeIndex++;
                currentMode = toMode(modes.get(modeIndex));
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
                            isLast ? dive.cns() : null,
                            currentMode));
        }
        return new DiveProfileUpload(computer.id(), start, end, measurements);
    }

    private static <T> @Nullable T getSampleAt(final List<T> samples, final int index) {
        return index < samples.size() ? samples.get(index) : null;
    }

    private static @Nullable DiveMode toMode(final DivesoftModeSample sample) {
        // sample.mode() isn't annotated @Nullable, but Jackson doesn't enforce that at
        // deserialization time - a malformed/absent value in the source JSON would otherwise throw
        // from the switch below instead of just leaving the mode unknown.
        if (sample.mode() == null) {
            return null;
        }
        return switch (sample.mode()) {
            case "ccr" -> DiveMode.CC;
            case "oc" -> DiveMode.OC;
            default -> null;
        };
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
