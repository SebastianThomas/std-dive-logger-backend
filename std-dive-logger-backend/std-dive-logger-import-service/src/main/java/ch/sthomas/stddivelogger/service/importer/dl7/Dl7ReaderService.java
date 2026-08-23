package ch.sthomas.stddivelogger.service.importer.dl7;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importer.dl7.Dl7Export;
import ch.sthomas.stddivelogger.model.importer.dl7.Dl7Sample;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAN DL7 ("Universal Dive Data Format") - a documented open standard, but Shearwater's own export
 * of it (unlike its native XML) carries no identifiable per-sample deco/TTS/NDL data - see
 * Dl7Export's doc comment. <b>Prefer the native XML export over DL7 when available</b> for real
 * deco/TTS signal; DL7 is still worth supporting for depth/time/temperature profiles from any real
 * DL7-exporting source, not just Shearwater.
 */
@Service
public class Dl7ReaderService extends BaseReaderService {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DiveService diveService;

    public Dl7ReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    /** A DL7 file's first line is always an FSH (File Start Header) segment. */
    public static boolean matches(final String textPrefix) {
        return textPrefix.startsWith("FSH|");
    }

    public ParsedImport parse(final User user, final String filename, final byte[] bytes) {
        final Dl7Export export;
        try {
            export = parseSegments(new String(bytes, StandardCharsets.UTF_8));
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException(
                    "Could not parse " + filename + " as a DL7 dive export.", e);
        }
        final var computer = getOrCreateComputer(user, export);
        final var profile = getDiveProfile(computer, export);
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
                PendingImportSource.DL7_SHEARWATER,
                null,
                filename,
                getDiveName(filename),
                null,
                null,
                null,
                computer.serialNumber(),
                profile.start(),
                Duration.between(profile.start(), profile.end()).toSeconds(),
                export.maxDepth(),
                payload);
    }

    private DiveComputer getOrCreateComputer(final User user, final Dl7Export export) {
        final var serial = export.deviceSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("DL7 export has no device serial number");
        }
        return diveService.getOrCreateDiveComputer(
                user, "Shearwater", serial, export.deviceModel());
    }

    DiveProfileUpload getDiveProfile(final DiveComputer computer, final Dl7Export export) {
        final var measurements =
                export.samples().stream()
                        .map(
                                s ->
                                        new DiveMeasurement(
                                                export.startTime()
                                                        .plusMillis(
                                                                Math.round(
                                                                        s.elapsedMinutes()
                                                                                * 60_000)),
                                                s.temperatureCelsius() != null
                                                        ? new Temperature(
                                                                s.temperatureCelsius(),
                                                                Temperature.TemperatureUnit.CELSIUS)
                                                        : null,
                                                s.depthMeters(),
                                                null,
                                                List.of(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null))
                        .toList();
        final var end = measurements.isEmpty() ? export.startTime() : measurements.getLast().time();
        return new DiveProfileUpload(computer.id(), export.startTime(), end, measurements);
    }

    private static Dl7Export parseSegments(final String content) {
        String deviceModel = "DL7 computer";
        @Nullable String deviceSerial = null;
        int diveNumber = 0;
        @Nullable Instant startTime = null;
        double maxDepth = 0;
        final var samples = new ArrayList<Dl7Sample>();
        var inProfileBlock = false;

        for (final var rawLine : content.split("\r?\n")) {
            final var line = rawLine.stripTrailing();
            if (line.equals("ZDP{")) {
                inProfileBlock = true;
                continue;
            }
            if (line.equals("ZDP}")) {
                inProfileBlock = false;
                continue;
            }
            if (inProfileBlock) {
                final var fields = line.split("\\|", -1);
                // fields[0] is always empty (leading pipe); [1]=elapsed minutes, [2]=depth,
                // [8]=temperature - see Dl7Sample's doc comment for why nothing else is parsed.
                if (fields.length > 8 && !fields[1].isBlank() && !fields[2].isBlank()) {
                    final var temp = fields[8].isBlank() ? null : Double.parseDouble(fields[8]);
                    samples.add(
                            new Dl7Sample(
                                    Double.parseDouble(fields[1]),
                                    Double.parseDouble(fields[2]),
                                    temp));
                }
                continue;
            }
            if (line.startsWith("ZRH|")) {
                final var fields = line.split("\\|", -1);
                if (fields.length > 3) {
                    deviceModel = fields[2];
                    deviceSerial = fields[3].isBlank() ? null : fields[3];
                }
            } else if (line.startsWith("ZDH|")) {
                final var fields = line.split("\\|", -1);
                if (fields.length > 5) {
                    diveNumber = Integer.parseInt(fields[2]);
                    startTime = parseTimestamp(fields[5]);
                }
            } else if (line.startsWith("ZDT|")) {
                final var fields = line.split("\\|", -1);
                if (fields.length > 3) {
                    maxDepth = Double.parseDouble(fields[3]);
                }
            }
        }
        if (startTime == null) {
            throw new IllegalArgumentException("DL7 export has no ZDH dive-header segment");
        }
        return new Dl7Export(deviceModel, deviceSerial, diveNumber, startTime, maxDepth, samples);
    }

    /**
     * No timezone in the source ("20260822101349") - same reasoning as ShearwaterXmlReaderService:
     * treated as UTC directly to match this project's already-tested UDDF behavior for the
     * identical wall-clock value from the same device.
     */
    private static Instant parseTimestamp(final String raw) {
        return LocalDateTime.parse(raw, TIMESTAMP_FORMAT).toInstant(ZoneOffset.UTC);
    }
}
