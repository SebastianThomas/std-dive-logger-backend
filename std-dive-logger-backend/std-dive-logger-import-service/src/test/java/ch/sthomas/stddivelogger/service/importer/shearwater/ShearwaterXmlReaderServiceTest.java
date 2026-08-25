package ch.sthomas.stddivelogger.service.importer.shearwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterDiveLog;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterDiveLogRecord;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Real, anonymized (device serial replaced) Shearwater Cloud "Source File" native XML export - the
 * same physical dive as the sibling {@code shearwater-perdix2.uddf}/{@code .zxu} fixtures used by
 * UddfReaderServiceTest/Dl7ReaderServiceTest, provided directly by a user to confirm this format
 * (unlike UDDF/DL7) carries real per-sample TTS.
 */
class ShearwaterXmlReaderServiceTest {
    private static final XmlMapper xmlMapper = ObjectMapperUtils.xmlMapperBuilder(c -> {}).build();
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);
    private static final String FIXTURE = "shearwater-perdix2-native.xml";

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

    private static ch.sthomas.stddivelogger.service.importer.ParsedImport parseFixture()
            throws IOException {
        final var service =
                new ShearwaterXmlReaderService(xmlMapper, diveServiceReturningComputer());
        try (final InputStream inputStream =
                ShearwaterXmlReaderServiceTest.class
                        .getClassLoader()
                        .getResourceAsStream(FIXTURE)) {
            final var bytes =
                    Objects.requireNonNull(inputStream, "fixture not found").readAllBytes();
            return service.parse(user, FIXTURE, bytes);
        }
    }

    @Test
    void parsesTheRealFixtureWithoutThrowing() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles()).hasSize(1);
    }

    @Test
    void tagsTheImportAsXmlShearwater() throws IOException {
        assertThat(parseFixture().source()).isEqualTo(PendingImportSource.XML_SHEARWATER);
    }

    @Test
    void usesTheRealAnonymizedDeviceSerial() throws IOException {
        assertThat(parseFixture().computerSerial()).isEqualTo("1000000099");
    }

    @Test
    void averagePpo2IsCalculatedNotMeasuredOnOpenCircuit() throws IOException {
        // The whole real fixture is OC/BO throughout (no real O2 sensor exists on open circuit)
        // - averagePPO2 there is this device's own FO2 x ambient-pressure estimate, not a sensor
        // reading, so it belongs under `calculated`, never `measured`.
        final var measurements = parseFixture().payload().profiles().getFirst().measurements();
        assertThat(measurements).isNotEmpty();
        assertThat(measurements)
                .allSatisfy(
                        m -> {
                            final var po2 = Objects.requireNonNull(m.po2());
                            assertThat(po2.measured()).isNull();
                            assertThat(po2.calculated()).isNotNull().isPositive();
                        });
    }

    @Test
    void averagePpo2IsMeasuredOnClosedCircuit() {
        // Synthetic, not from the real fixture (which never switches to CC) - a CCR sample's
        // averagePPO2 is this device's real redundant-sensor reading (see
        // ShearwaterDiveLogRecord's sensor1/2/3Millivolts fields), the one case that belongs
        // under `measured`.
        final var record =
                new ShearwaterDiveLogRecord(0, 20.0, 22.0, 0, 0, 0, 0, 0.21, 0.0, 1.2, "CC");
        final var log =
                new ShearwaterDiveLog(
                        1, "8/22/2026 10:00:00 AM", "8/22/2026 10:30:00 AM", 20.0, 1800, 0.0,
                        "1000000099", "2", java.util.List.of(record));
        final var service = new ShearwaterXmlReaderService(xmlMapper, diveServiceReturningComputer());
        final var computer =
                new DiveComputer(1L, new DiveComputerManufacturer(1L, "Shearwater"), "serial", "id", null);

        final var profile = service.getDiveProfile(computer, log);

        final var po2 = Objects.requireNonNull(profile.measurements().getFirst().po2());
        assertThat(po2.measured()).isEqualTo(1.2);
        assertThat(po2.calculated()).isNull();
    }

    @Test
    void startDateIsTheDeviceLocalWallClockTreatedAsUtc() throws IOException {
        // Cross-checked against this device's own UDDF export of the identical dive, which
        // stamps the same wall-clock reading with a "Z" (UTC) suffix rather than a real
        // conversion - see ShearwaterXmlReaderService.parseStartDate's doc comment.
        assertThat(parseFixture().startDate()).isEqualTo(Instant.parse("2026-08-22T10:13:49Z"));
    }

    @Test
    void maxDepthMatchesTheDevicesOwnSummaryField() throws IOException {
        assertThat(parseFixture().maxDepth()).isEqualTo(42.7);
    }

    @Test
    void profileHasOneMeasurementPerRecord() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles().getFirst().measurements()).hasSize(806);
    }

    @Test
    void maxTimeToSurfaceAcrossTheDiveIsTwelveMinutesARealDecoObligation() throws IOException {
        final var result = parseFixture();
        final var maxTts =
                result.payload().profiles().getFirst().measurements().stream()
                        .map(m -> m.timeToSurface())
                        .filter(Objects::nonNull)
                        .mapToLong(Duration::toMinutes)
                        .max()
                        .orElseThrow();
        assertThat(maxTts).isEqualTo(12L);
    }

    @Test
    void decoStopAppearsOnlyWhenFirstStopDepthIsPositive() throws IOException {
        final var result = parseFixture();
        final var measurements = result.payload().profiles().getFirst().measurements();
        // The dive has a real mandatory-stop period (firstStopDepth 6m/9m) and a long
        // no-deco/descent period beforehand (firstStopDepth 0) - both must be represented.
        assertThat(measurements).anyMatch(m -> !Objects.requireNonNull(m.deco()).isEmpty());
        assertThat(measurements).anyMatch(m -> Objects.requireNonNull(m.deco()).isEmpty());
    }

    @Test
    void everyMeasurementIsTaggedOcSinceThisDiveNeverLeftBailoutCircuit() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles().getFirst().measurements())
                .allSatisfy(m -> assertThat(m.mode()).isEqualTo(DiveMode.OC));
    }

    @Test
    void endOfDiveCnsLandsOnlyOnTheLastMeasurement() throws IOException {
        final var result = parseFixture();
        final var measurements = result.payload().profiles().getFirst().measurements();
        assertThat(measurements.get(0).cns()).isNull();
        assertThat(measurements.getLast().cns()).isEqualTo(6.0);
    }
}
