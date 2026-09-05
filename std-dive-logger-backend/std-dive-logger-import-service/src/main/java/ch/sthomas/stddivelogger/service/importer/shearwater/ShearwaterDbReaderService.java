package ch.sthomas.stddivelogger.service.importer.shearwater;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterDbDive;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterPnfLog;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterTankProfileData;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.BaseReaderService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Imports a whole Shearwater Cloud logbook at once, from the app's own SQLite database (its
 * "Backup"/data file, {@code <date>.db}) - one upload instead of exporting and uploading one XML
 * per dive.
 *
 * <p>Two tables carry everything usable, joined on the dive id:
 *
 * <ul>
 *   <li>{@code dive_details} - what the diver typed in the app: dive number, location, buddy,
 *       notes, visibility, tank pressures/size, weight.
 *   <li>{@code log_data} - the dive computer's own recording, as a gzipped native (PNF) binary blob
 *       in {@code data_bytes_1}; see {@link ShearwaterPnfParser}.
 * </ul>
 *
 * <p>The app's other tables are deliberately <b>not</b> read. {@code dive_logs} / {@code
 * dive_log_records} / {@code dive_site} / {@code StoredDiveComputer} look like the obvious sources
 * (they have per-sample columns and a proper site table), but they are <b>empty in a real
 * export</b> - Shearwater Cloud keeps the profile only in the {@code log_data} blob and re-derives
 * those tables on demand. An earlier attempt at this import read exactly those tables and so never
 * produced a single dive; the blob is the only place the samples actually live.
 *
 * <p>Per-dive failures are collected as errors rather than failing the whole upload - a logbook
 * with one corrupt blob still imports its other 94 dives.
 */
@Service
public class ShearwaterDbReaderService extends BaseReaderService {
    private static final Logger logger = LoggerFactory.getLogger(ShearwaterDbReaderService.class);

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** Shearwater stores tank pressures in psi; this project is bar throughout. */
    private static final double BAR_PER_PSI = 0.0689475729;

    /** "12l" or "2x 12l" - the multiplier is a bottle count, the value is the per-bottle size. */
    private static final Pattern TANK_SIZE =
            Pattern.compile(
                    "^\\s*(?:(\\d+)\\s*x\\s*)?(\\d+(?:[.,]\\d+)?)\\s*l\\s*$",
                    Pattern.CASE_INSENSITIVE);

    /** "4kg". Anything else (a range, a note) is left unparsed rather than half-read. */
    private static final Pattern WEIGHT =
            Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*kg\\s*$", Pattern.CASE_INSENSITIVE);

    /** "4m" / "10 m" / "4". Free text like "not great" or a range "2-8m" keeps only the text. */
    private static final Pattern VISIBILITY_METERS =
            Pattern.compile("^\\s*(\\d+(?:[.,]\\d+)?)\\s*m?\\s*$", Pattern.CASE_INSENSITIVE);

    /** "95" or the app's "attach to dive 10" marker "10.1"; see {@link #toDiveNumber}. */
    private static final Pattern DIVE_NUMBER = Pattern.compile("^\\s*(\\d+)(?:\\.(\\d+))?\\s*$");

    private static final String DIVE_QUERY =
            """
            SELECT d.DiveId, d.FileName, d.DiveDate, d.SerialNumber, d.DiveNumber, d.Location,
                   d.Site, d.Buddy, d.Notes, d.Visibility, d.Environment, d.Weight, d.TankSize,
                   d.Apparatus, d.TankProfileData, l.data_bytes_1
            FROM dive_details d
            JOIN log_data l ON l.log_id = d.DiveId
            WHERE l.data_bytes_1 IS NOT NULL
            ORDER BY d.DiveDate
            """;

    private final DiveService diveService;

    public ShearwaterDbReaderService(final DiveService diveService) {
        this.diveService = diveService;
    }

    /**
     * Reads every dive out of the uploaded database. SQLite needs a real file rather than a stream,
     * so the upload is spooled to a temp file that is always deleted again - including when the
     * file turns out not to be a Shearwater database at all.
     */
    public Stream<ParsedImportResultStreaming> parse(
            final User user, final String filename, final InputStream inputStream)
            throws IOException {
        final var tempFile = Files.createTempFile("shearwater-import", ".db");
        try (inputStream) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return readDives(user, filename, tempFile).stream();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private List<ParsedImportResultStreaming> readDives(
            final User user, final String filename, final Path databaseFile) {
        // Read-only, so nothing an uploaded file contains can be written back, and no journal or
        // -wal sidecar is created next to the temp file.
        try (final var connection =
                DriverManager.getConnection(
                        "jdbc:sqlite:" + databaseFile.toAbsolutePath() + "?open_mode=1")) {
            requireShearwaterSchema(connection, filename);
            return readDives(user, filename, connection);
        } catch (final SQLException e) {
            throw new IllegalArgumentException(
                    "Could not read " + filename + " as a Shearwater Cloud database.", e);
        }
    }

    /**
     * Fails fast with a message naming the actual problem, rather than letting a "no such table"
     * SQL error out of the first query - a `.db` upload could be any SQLite file at all.
     */
    private static void requireShearwaterSchema(final Connection connection, final String filename)
            throws SQLException {
        try (final var statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name"
                                        + " IN ('dive_details', 'log_data')");
                final var result = statement.executeQuery()) {
            if (!result.next() || result.getInt(1) < 2) {
                throw new IllegalArgumentException(
                        filename
                                + " is not a Shearwater Cloud database - it has no dive_details /"
                                + " log_data tables.");
            }
        }
    }

    private List<ParsedImportResultStreaming> readDives(
            final User user, final String filename, final Connection connection)
            throws SQLException {
        final var results = new ArrayList<ParsedImportResultStreaming>();
        try (final var statement = connection.prepareStatement(DIVE_QUERY);
                final var rows = statement.executeQuery()) {
            while (rows.next()) {
                final var dive = toDive(rows);
                results.add(parseOneSafe(user, filename, dive));
            }
        }
        if (results.isEmpty()) {
            return List.of(
                    new ParsedImportResultStreaming(
                            Stream.empty(),
                            Stream.of(
                                    "No dives with a recorded profile found in "
                                            + filename
                                            + ".")));
        }
        return results;
    }

    private static ShearwaterDbDive toDive(final ResultSet rows) throws SQLException {
        return new ShearwaterDbDive(
                rows.getString("DiveId"),
                rows.getString("FileName"),
                rows.getString("DiveDate"),
                rows.getString("SerialNumber"),
                rows.getString("DiveNumber"),
                rows.getString("Location"),
                rows.getString("Site"),
                rows.getString("Buddy"),
                rows.getString("Notes"),
                rows.getString("Visibility"),
                rows.getString("Environment"),
                rows.getString("Weight"),
                rows.getString("TankSize"),
                rows.getString("Apparatus"),
                rows.getString("TankProfileData"),
                rows.getBytes("data_bytes_1"));
    }

    private ParsedImportResultStreaming parseOneSafe(
            final User user, final String filename, final ShearwaterDbDive dive) {
        try {
            return new ParsedImportResultStreaming(
                    Stream.of(parseOne(user, filename, dive)), Stream.empty());
        } catch (final RuntimeException e) {
            logger.info("Could not parse dive {} of Shearwater database", dive.diveId(), e);
            return new ParsedImportResultStreaming(
                    Stream.empty(),
                    Stream.of(
                            "Could not import dive "
                                    + describe(dive)
                                    + " from "
                                    + filename
                                    + ": "
                                    + e.getMessage()));
        }
    }

    private static String describe(final ShearwaterDbDive dive) {
        final var number = dive.diveNumber();
        return number != null && !number.isBlank() ? "#" + number : dive.diveId();
    }

    ParsedImport parseOne(final User user, final String filename, final ShearwaterDbDive dive) {
        final var log = ShearwaterPnfParser.parseCompressed(dive.logData());
        final var computer = getOrCreateComputer(user, dive, log);
        final var profile = toProfile(computer, log);
        final var tankProfile = parseTankProfile(dive);
        final var payload =
                new PendingImportPayload(
                        List.of(profile),
                        dive.notes() == null ? "" : dive.notes(),
                        toVisibility(dive.visibility()),
                        DiveGasConsumption.EMPTY,
                        toConfiguration(user, dive, log, tankProfile),
                        toBuddies(dive.buddy()),
                        toDiveNumber(dive.diveNumber()));
        final var siteName = firstNonBlank(dive.location(), dive.site());
        return new ParsedImport(
                PendingImportSource.DB_SHEARWATER,
                dive.diveId(),
                filename,
                siteName != null ? siteName : getDiveName(filename),
                siteName,
                // Shearwater's GnssEntry/ExitLocation columns exist but are empty in every dive of
                // the only real database available to check the format against - see
                // ShearwaterPnfParser's note on the equivalent binary GNSS record.
                null,
                null,
                computer.serialNumber(),
                profile.start(),
                Duration.between(profile.start(), profile.end()).toSeconds(),
                log.maxDepthMeters(),
                payload);
    }

    private DiveComputer getOrCreateComputer(
            final User user, final ShearwaterDbDive dive, final ShearwaterPnfLog log) {
        // dive_details.SerialNumber is the hex string the app displays ("A3B6F031"); the binary
        // log's own final record carries the same value as a number. Prefer the displayed form so
        // an already-imported computer from an XML/UDDF import of the same device matches.
        final var serial =
                firstNonBlank(
                        dive.serialNumber(),
                        log.serialNumber() == null
                                ? null
                                : Long.toHexString(log.serialNumber()).toUpperCase(Locale.ROOT));
        if (serial == null) {
            throw new IllegalArgumentException("Shearwater dive has no device serial number.");
        }
        return diveService.getOrCreateDiveComputer(
                user,
                "Shearwater",
                serial,
                log.model() == null ? "Shearwater" : "Shearwater model " + log.model());
    }

    private static DiveProfileUpload toProfile(
            final DiveComputer computer, final ShearwaterPnfLog log) {
        final var samples = log.samples();
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Shearwater dive log has no samples.");
        }
        final var start = log.start();
        final var measurements = new ArrayList<DiveMeasurement>(samples.size());
        for (final var sample : samples) {
            final var mode = sample.closedCircuit() ? DiveMode.CC : DiveMode.OC;
            measurements.add(
                    new DiveMeasurement(
                            start.plus(sample.time()),
                            new Temperature(
                                    sample.temperatureCelsius(),
                                    Temperature.TemperatureUnit.CELSIUS),
                            sample.depthMeters(),
                            sample.inMandatoryDeco() ? null : sample.stopOrNdlTime(),
                            sample.inMandatoryDeco()
                                    ? List.of(
                                            new DecoStop(
                                                    "mandatory",
                                                    sample.decoStopDepthMeters(),
                                                    sample.stopOrNdlTime().toSeconds()))
                                    : List.of(),
                            sample.o2Percent() > 0
                                    ? new Gas(
                                            sample.o2Percent() / 100.0,
                                            sample.heliumPercent() / 100.0)
                                    : null,
                            // On a closed loop the device's averaged PPO2 is a real cell reading;
                            // on open circuit there is no O2 sensor at all and the same field is
                            // the device's own FO2 x ambient-pressure estimate. Same split (and
                            // same reason) as ShearwaterXmlReaderService.
                            sample.closedCircuit()
                                    ? PO2.fromOrNull(sample.setpoint(), sample.averagePpo2(), null)
                                    : PO2.fromOrNull(null, null, sample.averagePpo2()),
                            null,
                            null,
                            null,
                            sample.cns(),
                            mode,
                            sample.timeToSurface()));
        }
        return new DiveProfileUpload(
                computer.id(), start, start.plus(samples.getLast().time()), measurements);
    }

    private DiveConfiguration toConfiguration(
            final User user,
            final ShearwaterDbDive dive,
            final ShearwaterPnfLog log,
            final @Nullable ShearwaterTankProfileData tankProfile) {
        final var empty = DiveConfiguration.createEmpty(user);
        return new DiveConfiguration(
                empty.suit(),
                toBaseConfiguration(dive.apparatus()),
                parseDecimal(dive.weight(), WEIGHT),
                empty.weightFeeling(),
                toCylinders(dive, log, tankProfile),
                empty.ccrUnit(),
                empty.secondaryCcrUnit(),
                empty.adHocSuitType());
    }

    /**
     * One cylinder per tank the diver actually entered a pressure for, sized from {@code TankSize}
     * (a per-bottle size, so a "2x 12l" sidemount pair is two 12 L cylinders, not one 24 L).
     *
     * <p>{@code usageWindows} is deliberately left empty even though {@code GasProfiles} carries
     * per-gas time ranges: those ranges describe when a <i>gas</i> was breathed, not which
     * <i>bottle</i> it came from, so on the common two-bottles-one-gas rig there is no sound way to
     * split them across cylinders. An empty window list already means "the whole dive" for a single
     * role (see {@link DiveConfigurationCylinder}), and the gas switches themselves are preserved
     * per-sample on the profile, which is what the frontend's window suggestions read anyway.
     */
    private static List<DiveConfigurationCylinder> toCylinders(
            final ShearwaterDbDive dive,
            final ShearwaterPnfLog log,
            final @Nullable ShearwaterTankProfileData tankProfile) {
        if (tankProfile == null || tankProfile.tankData() == null) {
            return List.of();
        }
        final var size = toCylinderSize(dive.tankSize());
        final var closedCircuit = log.mode().isClosedCircuit();
        final var cylinders = new ArrayList<DiveConfigurationCylinder>();
        for (final var tank : tankProfile.tankData()) {
            if (!tank.hasPressure()) {
                continue;
            }
            final var gasProfile = tank.gasProfile();
            final var gas =
                    gasProfile == null || gasProfile.o2Percent() <= 0
                            ? Gas.AIR
                            : new Gas(
                                    gasProfile.o2Percent() / 100.0, gasProfile.hePercent() / 100.0);
            cylinders.add(
                    new DiveConfigurationCylinder(
                            0,
                            size,
                            // Material is not recorded anywhere in the database; the persistence
                            // layer infers it from the litre volume.
                            null,
                            toBar(tank.startPressurePsi()),
                            toBar(tank.endPressurePsi()),
                            "",
                            gas,
                            // Only the diver's own OC bottles are listed under TankData; a CCR
                            // dive's are what they bail out onto.
                            closedCircuit ? CylinderRole.BAILOUT : CylinderRole.OC,
                            List.of()));
        }
        return List.copyOf(cylinders);
    }

    private @Nullable ShearwaterTankProfileData parseTankProfile(final ShearwaterDbDive dive) {
        final var raw = dive.tankProfileData();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(raw, ShearwaterTankProfileData.class);
        } catch (final JacksonException e) {
            logger.info("Ignoring unreadable TankProfileData on dive {}", dive.diveId(), e);
            return null;
        }
    }

    private static CylinderSize toCylinderSize(final @Nullable String tankSize) {
        if (tankSize != null) {
            final var matcher = TANK_SIZE.matcher(tankSize);
            if (matcher.matches()) {
                return new CylinderSize(
                        CylinderSizeUnit.LITER,
                        Double.parseDouble(matcher.group(2).replace(',', '.')));
            }
        }
        // Nothing usable entered - 0 L keeps the cylinder (and its pressures) rather than dropping
        // it, and reads as "size not known" to the consumption maths.
        return new CylinderSize(CylinderSizeUnit.LITER, 0);
    }

    private static @Nullable Double toBar(final @Nullable String psi) {
        if (psi == null || psi.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(psi.trim()) * BAR_PER_PSI;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable BaseConfiguration toBaseConfiguration(
            final @Nullable String apparatus) {
        if (apparatus == null) {
            return null;
        }
        return switch (apparatus.trim().toLowerCase(Locale.ROOT)) {
            case "sidemount" -> BaseConfiguration.SIDEMOUNT;
            case "single tank", "doubles" -> BaseConfiguration.BACKMOUNT;
            default -> null;
        };
    }

    /** Free text in the app; keep it verbatim as the description and only read a plain number. */
    private static Visibility toVisibility(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Visibility.EMPTY;
        }
        return new Visibility(parseDecimal(raw, VISIBILITY_METERS), raw.trim(), null);
    }

    private static @Nullable Double parseDecimal(
            final @Nullable String raw, final Pattern pattern) {
        if (raw == null) {
            return null;
        }
        final var matcher = pattern.matcher(raw);
        return matcher.matches() ? Double.parseDouble(matcher.group(1).replace(',', '.')) : null;
    }

    /** A single free-text field in the app; commas/ampersands are how divers list several. */
    private static List<String> toBuddies(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Stream.of(raw.split("[,&]|\\band\\b"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * Shearwater Cloud's dive number is free-form text with three real shapes in practice:
     *
     * <ul>
     *   <li>a plain number ("95");
     *   <li>a <b>fractional</b> one ("10.1") - the app's marker for a second recording of an
     *       already-numbered dive, which is exactly what this project's fractional {@link
     *       DiveNumber} means too, so commit attaches it to dive 10 rather than creating a new one;
     *   <li>{@code -1} (or {@code -1.1}) - the app's "not numbered" sentinel, which must not become
     *       dive number 1.
     * </ul>
     *
     * Anything else is left as no guess rather than half-read; the diver still names the dive at
     * commit time.
     */
    private static @Nullable DiveNumber toDiveNumber(final @Nullable String raw) {
        if (raw == null) {
            return null;
        }
        final var matcher = DIVE_NUMBER.matcher(raw);
        if (!matcher.matches()) {
            return null;
        }
        try {
            final var number = Integer.parseInt(matcher.group(1));
            final var fraction = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            return new DiveNumber(number, fraction);
        } catch (final IllegalArgumentException e) {
            // Covers both an unparseable number and DiveNumber's own range validation.
            return null;
        }
    }

    private static @Nullable String firstNonBlank(final @Nullable String... values) {
        for (final var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
