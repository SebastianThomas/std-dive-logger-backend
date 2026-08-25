package ch.sthomas.stddivelogger.service.importer.shearwater;

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
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterDiveLog;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterXmlExport;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shearwater Cloud's own native per-dive XML ("Source File" export, not UDDF or DL7) - the richest
 * of the three Shearwater formats this project supports: real per-sample TTS ({@code ttsMins}),
 * next-mandatory-stop depth/time, and PPO2, none of which UDDF or DL7 carry for this device (see
 * AGENTS.md's Shearwater section). <b>Prefer this over UDDF/DL7 when available.</b>
 *
 * <p>The file's own {@code <?xml ... encoding="utf-16"?>} declaration is wrong - real exports seen
 * so far are plain UTF-8/ASCII bytes despite claiming utf-16, which makes a standard XML parser
 * reject them outright. {@link #decodeIgnoringWrongDeclaredEncoding} works around this by decoding
 * as UTF-8 and rewriting the declaration before parsing.
 */
@Service
public class ShearwaterXmlReaderService extends BaseReaderService {
    private static final DateTimeFormatter START_DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US);
    private static final Pattern WRONG_UTF16_DECLARATION =
            Pattern.compile("encoding=\"utf-16\"", Pattern.CASE_INSENSITIVE);

    private final XmlMapper xmlMapper;
    private final DiveService diveService;

    public ShearwaterXmlReaderService(final XmlMapper xmlMapper, final DiveService diveService) {
        this.xmlMapper = xmlMapper;
        this.diveService = diveService;
    }

    /** A Shearwater native export has a top-level "dive" object with a "diveLog" child. */
    public static boolean matches(final String xmlPrefix) {
        return Pattern.compile("<dive(\\s|>)").matcher(xmlPrefix).find()
                && xmlPrefix.contains("<diveLog>");
    }

    static String decodeIgnoringWrongDeclaredEncoding(final byte[] bytes) {
        final var text = new String(bytes, StandardCharsets.UTF_8);
        return WRONG_UTF16_DECLARATION.matcher(text).replaceFirst("encoding=\"utf-8\"");
    }

    public ParsedImport parse(final User user, final String filename, final byte[] bytes) {
        final ShearwaterXmlExport export;
        try {
            export =
                    xmlMapper.readValue(
                            decodeIgnoringWrongDeclaredEncoding(bytes), ShearwaterXmlExport.class);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException(
                    "Could not parse " + filename + " as a Shearwater dive export.", e);
        }
        final var log = export.diveLog();
        final var computer = getOrCreateComputer(user, log);
        final var profile = getDiveProfile(computer, log);
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
                PendingImportSource.XML_SHEARWATER,
                null,
                filename,
                getDiveName(filename),
                null, // No dive-site/GPS data in this format.
                null,
                null,
                computer.serialNumber(),
                profile.start(),
                Duration.between(profile.start(), profile.end()).toSeconds(),
                log.maxDepth(),
                payload);
    }

    private DiveComputer getOrCreateComputer(final User user, final ShearwaterDiveLog log) {
        final var serial = log.computerSerial();
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("Shearwater export has no device serial number");
        }
        return diveService.getOrCreateDiveComputer(
                user, "Shearwater", serial, "Shearwater model " + log.computerModel());
    }

    DiveProfileUpload getDiveProfile(final DiveComputer computer, final ShearwaterDiveLog log) {
        final var start = parseStartDate(log.startDate());
        final var records = log.diveLogRecords();
        final var measurements = new ArrayList<DiveMeasurement>(records.size());
        for (var i = 0; i < records.size(); i++) {
            final var record = records.get(i);
            final var isLast = i == records.size() - 1;
            final var mode = toMode(record.currentCircuitSetting());
            measurements.add(
                    new DiveMeasurement(
                            start.plusMillis(record.currentTime()),
                            new Temperature(
                                    record.waterTemp(), Temperature.TemperatureUnit.CELSIUS),
                            record.currentDepth(),
                            Duration.ofMinutes(record.currentNdl()),
                            record.firstStopDepth() > 0
                                    ? List.of(
                                            new DecoStop(
                                                    "mandatory",
                                                    record.firstStopDepth(),
                                                    Math.round(record.firstStopTime() * 60)))
                                    : List.of(),
                            new Gas(record.fractionO2(), record.fractionHe()),
                            // averagePPO2 is a real sensor reading only on a closed-circuit
                            // sample (this device's redundant O2 cells, see the record's own
                            // sensor1/2/3Millivolts fields) - on open circuit there's no PO2
                            // sensor at all, so the same field is Shearwater's own on-device
                            // FO2 x ambient-pressure estimate instead. The native XML format
                            // doesn't split this into two fields the way UDDF's
                            // measuredpo2/calculatedpo2 pair does, so the split has to be
                            // inferred from the circuit mode instead.
                            mode == DiveMode.CC
                                    ? new PO2(null, record.averagePPO2(), null)
                                    : new PO2(null, null, record.averagePPO2()),
                            // Tank pressure/SAC aren't modeled (see ShearwaterDiveLogRecord) - no
                            // AI transmitter paired in the exports seen so far.
                            null,
                            null,
                            null,
                            isLast ? log.endCns() : null,
                            mode,
                            Duration.ofMinutes(record.ttsMins())));
        }
        final var end =
                records.isEmpty() ? start : start.plusMillis(records.getLast().currentTime());
        return new DiveProfileUpload(computer.id(), start, end, measurements);
    }

    private static @Nullable DiveMode toMode(final String circuitSetting) {
        final var upper = circuitSetting.toUpperCase(Locale.ROOT);
        if (upper.contains("CC")) {
            return DiveMode.CC;
        }
        if (upper.contains("OC")) {
            return DiveMode.OC;
        }
        return null;
    }

    /**
     * No timezone in the source string ("8/22/2026 10:13:49 AM") - confirmed against this same
     * device's UDDF export for the identical dive, which stamps the identical wall-clock value with
     * a "Z" (UTC) suffix rather than a real conversion. Parsed as UTC here purely as a staging
     * placeholder consistent with that; {@code ImportService.correctForUnknownTimezone} corrects it
     * to the site's real timezone once one is known, at commit time (this format carries no GPS of
     * its own to resolve one any earlier).
     */
    static @NonNull Instant parseStartDate(final String raw) {
        return LocalDateTime.parse(raw, START_DATE_FORMAT).toInstant(ZoneOffset.UTC);
    }
}
