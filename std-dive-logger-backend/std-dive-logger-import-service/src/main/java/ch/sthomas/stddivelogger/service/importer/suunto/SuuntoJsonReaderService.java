package ch.sthomas.stddivelogger.service.importer.suunto;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDepthSummary;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDevice;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDiveExport;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDiving;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoHeader;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoSample;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses a Suunto app/SuuntoLink dive export - richer than the same computer's FIT export (NDL,
 * deco ceiling, per-sample gas switches, tissue loading), see {@code FitReaderService}'s Suunto
 * notes for what FIT lacks by comparison. Brand detection lives in {@link JsonReaderService}, which
 * calls {@link #matches(JsonNode)} before handing off bytes here - this class assumes it's already
 * a Suunto export.
 *
 * <p>Uses Jackson's {@code UPPER_CAMEL_CASE} naming strategy instead of {@code @JsonProperty} per
 * field - Suunto's keys are the Java field name with its first letter capitalized, so one mapper
 * config covers every field, and a future Suunto schema change only touches this one place.
 */
@Service
public class SuuntoJsonReaderService extends BaseReaderService {

    private static final JsonMapper JSON_MAPPER =
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
                    .build();

    private final DiveService diveService;

    public SuuntoJsonReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    /** A Suunto export has a top-level "DeviceLog" object with "Header" and "Samples". */
    public static boolean matches(final JsonNode root) {
        final var deviceLog = root.path("DeviceLog");
        return deviceLog.isObject() && deviceLog.has("Header") && deviceLog.has("Samples");
    }

    /**
     * Doesn't touch the dive/site tables - only the dive computer is resolved eagerly
     * (get-or-create by serial number is idempotent, so doing it now rather than at commit is
     * harmless even if the staged import is later discarded).
     */
    public ParsedImport parse(final User user, final String filename, final byte[] bytes) {
        final SuuntoDiveExport export;
        try {
            export = JSON_MAPPER.readValue(bytes, SuuntoDiveExport.class);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException(
                    "Could not parse " + filename + " as a Suunto dive export.", e);
        }
        final var header = export.deviceLog().header();
        final var computer = getOrCreateComputer(user, header.device());
        final var gases = getGases(header.diving());
        final var profile = getDiveProfile(computer, header, export.deviceLog().samples(), gases);
        final var payload =
                new PendingImportPayload(
                        List.of(profile),
                        "",
                        Visibility.EMPTY,
                        DiveGasConsumption.EMPTY,
                        DiveConfiguration.createEmpty(user),
                        List.of(),
                        null);
        return new ParsedImport(
                PendingImportSource.JSON_SUUNTO,
                null,
                filename,
                getDiveName(filename),
                null, // No dive-site/GPS data in this format.
                null,
                null,
                computer.serialNumber(),
                profile.start(),
                Duration.between(profile.start(), profile.end()).toSeconds(),
                Optional.ofNullable(header.depth()).map(SuuntoDepthSummary::max).orElse(null),
                payload);
    }

    private DiveComputer getOrCreateComputer(final User user, final SuuntoDevice device) {
        final var serial = device.serialNumber();
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException(
                    "Suunto export for device \"" + device.name() + "\" has no serial number");
        }
        return diveService.getOrCreateDiveComputer(user, "Suunto", serial, device.name());
    }

    static @NonNull List<Gas> getGases(final @Nullable SuuntoDiving diving) {
        if (diving == null || diving.gases() == null) {
            return List.of();
        }
        return diving.gases().stream().map(g -> new Gas(g.oxygen(), g.helium())).toList();
    }

    DiveProfileUpload getDiveProfile(
            final DiveComputer computer,
            final SuuntoHeader header,
            final List<SuuntoSample> samples,
            final List<Gas> gases) {
        final var start = parseInstant(header.dateTime());
        final var end = start.plusMillis(Math.round(header.duration() * 1000));
        final var measurements = new ArrayList<DiveMeasurement>(samples.size());
        var currentGasIndex = 0;
        for (final var sample : samples) {
            currentGasIndex = updatedGasIndex(sample, currentGasIndex);
            // First sample (and possibly others) is an events-only marker with no Depth - no
            // measurement to emit, just a gas-index update to carry forward.
            if (sample.depth() == null) {
                continue;
            }
            final var gas =
                    currentGasIndex >= 0 && currentGasIndex < gases.size()
                            ? gases.get(currentGasIndex)
                            : null;
            measurements.add(
                    new DiveMeasurement(
                            parseInstant(sample.timeISO8601()),
                            sample.temperature() != null
                                    ? new Temperature(
                                            sample.temperature(),
                                            Temperature.TemperatureUnit.KELVIN)
                                    : null,
                            sample.depth(),
                            sample.noDecTime() != null
                                    ? Duration.ofSeconds(sample.noDecTime())
                                    : null,
                            sample.ceiling() != null && sample.ceiling() > 0
                                    ? List.of(
                                            new DecoStop(
                                                    "ceiling",
                                                    sample.ceiling(),
                                                    sample.timeToSurface() != null
                                                            ? sample.timeToSurface()
                                                            : 0))
                                    : List.of(),
                            gas,
                            // No PO2/setpoint, RMV/SAC, N2/O2Tox/CNS, or CCR mode in this format.
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            sample.timeToSurface() != null
                                    ? Duration.ofSeconds(sample.timeToSurface())
                                    : null));
        }
        return new DiveProfileUpload(computer.id(), start, end, measurements);
    }

    /** GasNumber is 1-based (index into Diving.Gases). Last gas-switch event in a sample wins. */
    private static int updatedGasIndex(final SuuntoSample sample, final int currentIndex) {
        if (sample.events() == null) {
            return currentIndex;
        }
        var index = currentIndex;
        for (final var event : sample.events()) {
            if (event.gasSwitch() != null) {
                index = event.gasSwitch().gasNumber() - 1;
            }
        }
        return index;
    }

    static Instant parseInstant(final String iso8601) {
        return OffsetDateTime.parse(iso8601).toInstant();
    }
}
