package ch.sthomas.stddivelogger.service.importer.shearwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.gear.Suit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Covers the mapping from Shearwater Cloud's own SQLite schema onto this project's import model,
 * against a synthetic database built to the real schema (so it runs everywhere). The equivalent
 * check against a real 95-dive logbook is {@link ShearwaterDbFixtureTest}.
 */
class ShearwaterDbReaderServiceTest {
    private static final long START = 1_732_874_281L; // 2024-11-29T09:58:01Z
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);

    @TempDir Path tempDir;

    private static DiveService diveServiceReturningComputer() {
        final var diveService = mock(DiveService.class);
        when(diveService.getOrCreateDiveComputer(any(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocation ->
                                new DiveComputer(
                                        1L,
                                        new DiveComputerManufacturer(1L, invocation.getArgument(1)),
                                        invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        null));
        return diveService;
    }

    private Path database(final ShearwaterDbTestDatabase.Dive... dives) throws SQLException {
        final var file = tempDir.resolve("shearwater-test.db");
        ShearwaterDbTestDatabase.write(file, List.of(dives));
        return file;
    }

    private static byte[] logBlob() {
        return ShearwaterPnfTestLogs.gzipWithLengthPrefix(
                ShearwaterPnfTestLogs.build(
                        START,
                        400,
                        31.2,
                        14,
                        1,
                        5000,
                        List.of(
                                ShearwaterPnfTestLogs.Sample.openCircuit(5.0, 12),
                                ShearwaterPnfTestLogs.Sample.openCircuit(31.2, 8),
                                ShearwaterPnfTestLogs.Sample.openCircuit(3.0, 12))));
    }

    private static ShearwaterDbTestDatabase.Dive dive(final String number) {
        return new ShearwaterDbTestDatabase.Dive(
                "623206991764410" + number,
                number,
                "Kleiner Parkplatz",
                "Yancy Wolf",
                "Found Oscar @32",
                "4m",
                "4kg",
                "2x 12l",
                "Sidemount",
                """
                {"GasProfiles":[{"profileIndex":0,"O2Percent":21,"HePercent":0,"CircuitMode":1,\
                "StartTimeInSeconds":0.0,"EndTimeInSeconds":400.0}],\
                "TankData":[\
                {"StartPressurePSI":"2755.72","EndPressurePSI":"1595.41",\
                "GasProfile":{"profileIndex":0,"O2Percent":32,"HePercent":0,"CircuitMode":1}},\
                {"StartPressurePSI":"2610.68","EndPressurePSI":"1740.45",\
                "GasProfile":{"profileIndex":0,"O2Percent":21,"HePercent":0,"CircuitMode":1}},\
                {"StartPressurePSI":"","EndPressurePSI":"","GasProfile":null}]}\
                """,
                logBlob());
    }

    private List<ParsedImport> parseAll(final Path database) throws IOException {
        final var service = new ShearwaterDbReaderService(diveServiceReturningComputer());
        try (final var input = Files.newInputStream(database)) {
            return service.parse(user, database.getFileName().toString(), input)
                    .map(ParsedImportResultStreaming::toResult)
                    .flatMap(result -> result.parsed().stream())
                    .toList();
        }
    }

    private List<String> parseErrors(final Path database) throws IOException {
        final var service = new ShearwaterDbReaderService(diveServiceReturningComputer());
        try (final var input = Files.newInputStream(database)) {
            return service.parse(user, database.getFileName().toString(), input)
                    .map(ParsedImportResultStreaming::toResult)
                    .flatMap(result -> result.errors().stream())
                    .toList();
        }
    }

    @Test
    void readsEveryDiveInTheLogbook() throws Exception {
        final var parsed = parseAll(database(dive("93"), dive("94"), dive("95")));
        assertThat(parsed).hasSize(3);
        assertThat(parsed)
                .extracting(ParsedImport::source)
                .containsOnly(PendingImportSource.DB_SHEARWATER);
        assertThat(parsed)
                .extracting(p -> Objects.requireNonNull(p.payload().diveNumberGuess()).number())
                .containsExactly(93, 94, 95);
    }

    @Test
    void mapsTheDiveComputerAndProfile() throws Exception {
        final var parsed = parseAll(database(dive("95"))).getFirst();
        assertThat(parsed.computerSerial()).isEqualTo("A3B6F031");
        assertThat(parsed.startDate()).isEqualTo(Instant.ofEpochSecond(START));
        assertThat(parsed.maxDepth()).isEqualTo(31.2);

        final var profile = parsed.payload().profiles().getFirst();
        assertThat(profile.measurements()).hasSize(3);
        assertThat(profile.start()).isEqualTo(Instant.ofEpochSecond(START));
        // Three samples at the 5s interval, so the last one is 15s in.
        assertThat(profile.end()).isEqualTo(Instant.ofEpochSecond(START).plusSeconds(15));
        assertThat(profile.measurements()).extracting("depth").containsExactly(5.0, 31.2, 3.0);
        assertThat(profile.measurements())
                .allSatisfy(
                        m -> {
                            assertThat(m.mode()).isEqualTo(DiveMode.OC);
                            // Open circuit has no O2 cell, so the device's PPO2 is a calculation,
                            // never a reading.
                            assertThat(Objects.requireNonNull(m.po2()).measured()).isNull();
                            assertThat(Objects.requireNonNull(m.po2()).calculated())
                                    .isEqualTo(0.21);
                            assertThat(m.ndl()).isEqualTo(Duration.ofMinutes(99));
                            assertThat(m.deco()).isEmpty();
                        });
    }

    @Test
    void mapsTheDiverEnteredMetadata() throws Exception {
        final var parsed = parseAll(database(dive("95"))).getFirst();
        assertThat(parsed.siteNameGuess()).isEqualTo("Kleiner Parkplatz");
        assertThat(parsed.diveIdentifierGuess()).isEqualTo("Kleiner Parkplatz");
        assertThat(parsed.externalId()).isEqualTo("62320699176441095");
        assertThat(parsed.payload().notes()).isEqualTo("Found Oscar @32");
        assertThat(parsed.payload().namedBuddies()).containsExactly("Yancy Wolf");
        // Free text in the app - keep it verbatim and only read a plain number out of it.
        assertThat(parsed.payload().visibility().meters()).isEqualTo(4.0);
        assertThat(parsed.payload().visibility().description()).isEqualTo("4m");
        assertThat(parsed.payload().configuration().weight()).isEqualTo(4.0);
        assertThat(parsed.payload().configuration().base()).isEqualTo(BaseConfiguration.SIDEMOUNT);
    }

    @Test
    void buildsOneCylinderPerTankTheDiverFilledIn() throws Exception {
        final var cylinders =
                parseAll(database(dive("95"))).getFirst().payload().configuration().cylinders();
        // Three TankData entries, but only the two with a pressure are real.
        assertThat(cylinders).hasSize(2);
        // "2x 12l" is a per-bottle size, so a sidemount pair is two 12 L bottles, not one 24 L.
        assertThat(cylinders).extracting(c -> c.size().liters()).containsOnly(12.0);
        assertThat(cylinders).extracting("role").containsOnly(CylinderRole.OC);
        // Shearwater stores psi; 2755.72 psi is ~190 bar.
        assertThat(Objects.requireNonNull(cylinders.getFirst().startBar()))
                .isCloseTo(190.0, within(0.5));
        assertThat(Objects.requireNonNull(cylinders.getFirst().endBar()))
                .isCloseTo(110.0, within(0.5));
        // Each bottle carries its own mix, not the dive's first gas profile.
        assertThat(cylinders.getFirst().gas().o2()).isEqualTo(0.32);
        assertThat(cylinders.getLast().gas().o2()).isEqualTo(0.21);
        // Gas-profile time ranges say when a gas was breathed, not from which bottle - see
        // ShearwaterDbReaderService.toCylinders.
        assertThat(cylinders).allSatisfy(c -> assertThat(c.usageWindows()).isEmpty());
    }

    @Test
    void keepsUnparseableFreeTextWithoutGuessingANumber() throws Exception {
        final var messy =
                new ShearwaterDbTestDatabase.Dive(
                        "1",
                        "1",
                        "Horn",
                        "Felice & Jaro",
                        null,
                        "not great",
                        "a lot",
                        "2-8l",
                        "Rebreather",
                        null,
                        logBlob());
        final var parsed = parseAll(database(messy)).getFirst();
        assertThat(parsed.payload().visibility().meters()).isNull();
        assertThat(parsed.payload().visibility().description()).isEqualTo("not great");
        assertThat(parsed.payload().configuration().weight()).isNull();
        assertThat(parsed.payload().configuration().base()).isNull();
        // Buddies are one free-text field in the app; "&"/","/"and" is how divers list several.
        assertThat(parsed.payload().namedBuddies()).containsExactly("Felice", "Jaro");
        // No TankProfileData at all - no cylinders rather than an invented one.
        assertThat(parsed.payload().configuration().cylinders()).isEmpty();
    }

    @Test
    void reportsOneBadDiveWithoutLosingTheRest() throws Exception {
        final var broken =
                new ShearwaterDbTestDatabase.Dive(
                        "broken",
                        "50",
                        "Somewhere",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        final var database = database(dive("49"), broken, dive("51"));
        assertThat(parseAll(database)).hasSize(2);
        assertThat(parseErrors(database))
                .singleElement()
                .asString()
                .contains("#50")
                .contains("not a gzipped native (PNF) log");
    }

    @Test
    void readsTheAppsThreeDiveNumberShapes() throws Exception {
        final var plain = dive("95");
        final var fractional = dive("10").withDiveNumber("10.1");
        final var unnumbered = dive("7").withDiveNumber("-1");
        final var parsed = parseAll(database(plain, fractional, unnumbered));

        assertThat(parsed.get(0).payload().diveNumberGuess())
                .isEqualTo(new ch.sthomas.stddivelogger.model.dive.DiveNumber(95));
        // "10.1" is the app's marker for a second recording of dive 10; a fractional guess is what
        // makes commit attach it to that dive rather than create a new one.
        final var second = Objects.requireNonNull(parsed.get(1).payload().diveNumberGuess());
        assertThat(second.number()).isEqualTo(10);
        assertThat(second.isFractional()).isTrue();
        // "-1" is the app's "not numbered" sentinel and must not become dive number 1.
        assertThat(parsed.get(2).payload().diveNumberGuess()).isNull();
    }

    @Test
    void rejectsASqliteFileThatIsNotAShearwaterLogbook() throws Exception {
        final var file = tempDir.resolve("something-else.db");
        try (final var connection =
                        DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                final var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE notes (id integer primary key, body varchar)");
        }
        assertThatThrownBy(() -> parseAll(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a Shearwater Cloud database");
    }

    @Test
    void rejectsAFileThatIsNotASqliteDatabaseAtAll() {
        final var service = new ShearwaterDbReaderService(diveServiceReturningComputer());
        assertThatThrownBy(
                        () ->
                                service.parse(
                                        user,
                                        "notes.db",
                                        new ByteArrayInputStream("not a database".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsALogbookWithNoProfilesInsteadOfSucceedingEmpty() throws Exception {
        final var database = database();
        assertThat(parseAll(database)).isEmpty();
        assertThat(parseErrors(database))
                .singleElement()
                .asString()
                .contains("No dives with a recorded profile");
    }

    @Test
    void neverWritesToTheUploadedDatabase() throws Exception {
        // The reader opens the upload read-only (open_mode=1), so nothing an uploaded file can
        // contain gets written back and no -wal/-journal sidecar appears next to it. Asserted
        // directly, because losing that flag from the JDBC URL would otherwise be invisible.
        final var database = database(dive("95"));
        final var before = Files.readAllBytes(database);
        parseAll(database);
        assertThat(Files.readAllBytes(database)).isEqualTo(before);
        try (final var files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.endsWith("-wal") || name.endsWith("-journal"));
        }
    }

    @Test
    void leavesNoTemporaryFileBehind() throws Exception {
        final var before = tempFileCount();
        parseAll(database(dive("95")));
        assertThat(tempFileCount()).isEqualTo(before);
    }

    private static long tempFileCount() throws IOException {
        try (final var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("shearwater-import"))
                    .count();
        }
    }

    @Test
    void suitIsLeftAsTheEmptyDefaultRatherThanGuessedFromTheDressField() throws Exception {
        // dive_details.Dress ("Dry Suit") is free text with no saved Suit behind it - mapping it
        // would invent gear in the diver's permanent list.
        final var suit = parseAll(database(dive("95"))).getFirst().payload().configuration().suit();
        assertThat(suit).isNotNull().isEqualTo(Suit.createUnknown(user));
    }
}
