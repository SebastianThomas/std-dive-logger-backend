package ch.sthomas.stddivelogger.service.importer.shearwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterDbCalculatedValues;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ParsedImport;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-checks the Shearwater native (PNF) decode against a real 95-dive Perdix 2 logbook, using
 * <b>Shearwater Cloud's own figures for the same dives</b> as ground truth: {@code
 * log_data.data_bytes_3} (the app's per-dive metadata: start, duration, max depth, dive mode) and
 * {@code log_data.calculated_values_from_samples} (its own roll-up of the very samples this parser
 * decodes). Nothing here is asserted against a value this project computed.
 *
 * <p>The fixture is one real diver's personal logbook, so it is {@code .gitignore}'d like the real
 * {@code .fit} fixtures next to it - these tests skip when it isn't present rather than fail. The
 * layout-level tests that always run are in {@link ShearwaterPnfParserTest}.
 */
class ShearwaterDbFixtureTest {
    private static final String FIXTURE = "2025-11-29.db";
    private static final int EXPECTED_DIVES = 95;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DIVE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);

    private record Row(
            String diveDate, String metadataJson, String calculatedJson, byte[] logBlob) {}

    private static Path fixture() throws IOException {
        try (final InputStream stream =
                ShearwaterDbFixtureTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            Assumptions.assumeTrue(
                    stream != null, FIXTURE + " not present (personal fixture, not committed)");
            final var copy = Files.createTempFile("shearwater-fixture", ".db");
            copy.toFile().deleteOnExit();
            Files.copy(stream, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        }
    }

    private static List<Row> rows() throws IOException, SQLException {
        final var file = fixture();
        final var rows = new ArrayList<Row>();
        try (final var connection =
                        DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
                final var statement =
                        connection.prepareStatement(
                                """
                                SELECT d.DiveDate, l.data_bytes_3,
                                       l.calculated_values_from_samples, l.data_bytes_1
                                FROM dive_details d JOIN log_data l ON l.log_id = d.DiveId
                                ORDER BY d.DiveDate
                                """);
                final var result = statement.executeQuery()) {
            while (result.next()) {
                rows.add(
                        new Row(
                                result.getString(1),
                                result.getString(2),
                                result.getString(3),
                                result.getBytes(4)));
            }
        }
        return rows;
    }

    /** {@code data_bytes_3} - the app's own per-dive header summary, in its own JSON shape. */
    private record Metadata(
            long StartTime, int DiveTimeInSeconds, double MaxDepth, int Mode, int DiveNumber) {}

    @Test
    void everyDiveDecodesToShearwatersOwnHeaderFigures() throws Exception {
        final var rows = rows();
        assertThat(rows).hasSize(EXPECTED_DIVES);
        for (final var row : rows) {
            final var expected = JSON_MAPPER.readValue(row.metadataJson(), Metadata.class);
            final var log = ShearwaterPnfParser.parseCompressed(row.logBlob());
            assertThat(log.start()).isEqualTo(Instant.ofEpochSecond(expected.StartTime()));
            assertThat(log.diveTime().toSeconds()).isEqualTo(expected.DiveTimeInSeconds());
            assertThat(log.maxDepthMeters()).isEqualTo(expected.MaxDepth());
            assertThat(log.mode().ordinal())
                    .isEqualTo(ShearwaterPnfLogModeOrdinal.of(expected.Mode()));
            assertThat(log.samples()).isNotEmpty();
        }
    }

    /** Keeps the mode comparison readable - the app stores the same codes the parser maps. */
    private static final class ShearwaterPnfLogModeOrdinal {
        private ShearwaterPnfLogModeOrdinal() {}

        static int of(final int code) {
            return ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterPnfLog
                    .DiveComputerMode.fromCode(code)
                    .ordinal();
        }
    }

    @Test
    void decodedSamplesMatchShearwatersOwnRollUpOfTheSameSamples() throws Exception {
        var totalDepthError = 0.0;
        var dives = 0;
        for (final var row : rows()) {
            final var expected =
                    JSON_MAPPER.readValue(row.calculatedJson(), ShearwaterDbCalculatedValues.class);
            final var samples = ShearwaterPnfParser.parseCompressed(row.logBlob()).samples();

            // Water temperature is a plain per-sample reading, so the extremes must match exactly.
            final var temperatures =
                    samples.stream().mapToDouble(s -> s.temperatureCelsius()).toArray();
            assertThat(java.util.Arrays.stream(temperatures).min().orElseThrow())
                    .isEqualTo(expected.minTemp());

            // The app averages depth over its own dive window; the sample list also covers the
            // surface time either side of it, so compare over the submerged span instead.
            final var submerged = samples.stream().filter(s -> s.depthMeters() >= 1.0).toList();
            if (!submerged.isEmpty()) {
                final var first = samples.indexOf(submerged.getFirst());
                final var last = samples.indexOf(submerged.getLast());
                final var average =
                        samples.subList(first, last + 1).stream()
                                .mapToDouble(s -> s.depthMeters())
                                .average()
                                .orElseThrow();
                totalDepthError +=
                        Math.abs(
                                average
                                        - java.util.Objects.requireNonNull(
                                                expected.averageDepth()));
                dives++;
            }
        }
        assertThat(dives).isEqualTo(EXPECTED_DIVES);
        // Averaged over the whole logbook, the decoded depth series lands on the app's own average
        // to within centimetres - a wrong scale, offset or record stride could not do that.
        assertThat(totalDepthError / dives).isLessThan(0.1);
    }

    @Test
    void theDeviceClockCarriesNoTimezone() throws Exception {
        // Every dive's binary clock reading, taken as a UTC epoch, is exactly the local wall clock
        // the app displays - i.e. the reading is timezone-less, the same as Shearwater's XML/UDDF/
        // DL7 exports. This is what puts DB_SHEARWATER in ImportService's
        // SOURCES_WITH_UNKNOWN_TIMEZONE, so the import is corrected once a real dive site is known.
        for (final var row : rows()) {
            final var log = ShearwaterPnfParser.parseCompressed(row.logBlob());
            final var displayed = LocalDateTime.parse(row.diveDate(), DIVE_DATE);
            assertThat(LocalDateTime.ofInstant(log.start(), ZoneOffset.UTC)).isEqualTo(displayed);
        }
    }

    @Test
    void theReaderStagesEveryDiveInTheLogbook() throws Exception {
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
        final var service = new ShearwaterDbReaderService(diveService);
        final List<ParsedImport> parsed;
        final List<String> errors;
        try (final var input = Files.newInputStream(fixture())) {
            final var results =
                    service.parse(user, FIXTURE, input)
                            .map(ParsedImportResultStreaming::toResult)
                            .toList();
            parsed = results.stream().flatMap(r -> r.parsed().stream()).toList();
            errors = results.stream().flatMap(r -> r.errors().stream()).toList();
        }
        assertThat(errors).isEmpty();
        assertThat(parsed).hasSize(EXPECTED_DIVES);
        assertThat(parsed)
                .allSatisfy(
                        p -> {
                            assertThat(p.payload().profiles()).hasSize(1);
                            assertThat(p.payload().profiles().getFirst().measurements())
                                    .isNotEmpty();
                            assertThat(p.computerSerial()).isEqualTo("A3B6F031");
                            assertThat(p.startDate()).isNotNull();
                            assertThat(p.maxDepth()).isNotNull().isPositive();
                        });
        // Dive numbers come straight from the app's free-text field, which in a real logbook holds
        // all three of its shapes: plain numbers, "10.1"-style markers for a second recording of an
        // already-numbered dive, and "-1" for dives the diver never numbered.
        final var guesses = parsed.stream().map(p -> p.payload().diveNumberGuess()).toList();
        assertThat(guesses).anyMatch(java.util.Objects::isNull);
        assertThat(guesses).anyMatch(n -> n != null && n.isFractional());
        assertThat(guesses.stream().filter(java.util.Objects::nonNull))
                .allSatisfy(n -> assertThat(n.number()).isPositive());
    }
}
