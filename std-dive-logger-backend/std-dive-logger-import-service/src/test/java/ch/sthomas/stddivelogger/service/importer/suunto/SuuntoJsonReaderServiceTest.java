package ch.sthomas.stddivelogger.service.importer.suunto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDevice;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoDiving;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoEvent;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoGas;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoGasSwitchEvent;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoHeader;
import ch.sthomas.stddivelogger.model.importer.suunto.SuuntoSample;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

class SuuntoJsonReaderServiceTest {

    private static final DiveComputer COMPUTER =
            new DiveComputer(
                    1L, new DiveComputerManufacturer(1L, "Suunto"), "123", "EON Core", null);

    private final SuuntoJsonReaderService service =
            new SuuntoJsonReaderService(mock(DiveService.class));

    private static SuuntoSample eventsOnlySample(final String time, final int gasNumber) {
        return new SuuntoSample(
                time,
                null,
                null,
                null,
                null,
                null,
                List.of(new SuuntoEvent(new SuuntoGasSwitchEvent(gasNumber))));
    }

    private static SuuntoSample depthSample(
            final String time,
            final double depth,
            final @Nullable Double temperatureKelvin,
            final @Nullable Double ceiling,
            final @Nullable Long noDecTime) {
        return depthSample(time, depth, temperatureKelvin, ceiling, noDecTime, null);
    }

    private static SuuntoSample depthSample(
            final String time,
            final double depth,
            final @Nullable Double temperatureKelvin,
            final @Nullable Double ceiling,
            final @Nullable Long noDecTime,
            final @Nullable Long timeToSurface) {
        return new SuuntoSample(
                time, depth, temperatureKelvin, ceiling, noDecTime, timeToSurface, null);
    }

    // --- getGases() -----------------------------------------------------------------------

    @Test
    void getGasesReturnsEmptyListWhenDivingIsNull() {
        assertThat(SuuntoJsonReaderService.getGases(null)).isEmpty();
    }

    @Test
    void getGasesReturnsEmptyListWhenGasesFieldIsNull() {
        assertThat(SuuntoJsonReaderService.getGases(new SuuntoDiving(null))).isEmpty();
    }

    @Test
    void getGasesConvertsEachEntryPreservingFractions() {
        final var gases =
                SuuntoJsonReaderService.getGases(
                        new SuuntoDiving(
                                List.of(new SuuntoGas(0.21, 0.0), new SuuntoGas(0.5, 0.0))));

        assertThat(gases).hasSize(2);
        assertThat(gases.get(0).o2()).isCloseTo(0.21, Offset.offset(0.0001));
        assertThat(gases.get(0).he()).isCloseTo(0.0, Offset.offset(0.0001));
        assertThat(gases.get(1).o2()).isCloseTo(0.5, Offset.offset(0.0001));
    }

    // --- getDiveProfile() -------------------------------------------------------------------

    @Test
    void eventsOnlyFirstSampleProducesNoMeasurementButStillUpdatesGasIndex() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(
                        eventsOnlySample("2026-01-01T10:00:00.000+00:00", 2),
                        depthSample("2026-01-01T10:00:10.000+00:00", 5.0, null, null, null));
        final var gases =
                List.of(
                        ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas.AIR,
                        new ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas(0.5, 0.0));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, gases);

        assertThat(profile.measurements()).hasSize(1);
        final var gas = profile.measurements().getFirst().gas();
        assertThat(gas).isNotNull();
        assertThat(Objects.requireNonNull(gas).o2()).isCloseTo(0.5, Offset.offset(0.0001));
    }

    @Test
    void gasSwitchEventNumberIsOneBasedConvertedToZeroBasedIndex() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(
                        eventsOnlySample("2026-01-01T10:00:00.000+00:00", 1),
                        depthSample("2026-01-01T10:00:10.000+00:00", 5.0, null, null, null));
        final var gases =
                List.of(
                        ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas.AIR,
                        new ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas(0.5, 0.0));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, gases);

        final var gas = profile.measurements().getFirst().gas();
        assertThat(gas).isNotNull();
        // GasNumber=1 -> index 0 -> the first (air) gas, not the second.
        assertThat(Objects.requireNonNull(gas).o2()).isCloseTo(0.21, Offset.offset(0.001));
    }

    @Test
    void outOfRangeGasIndexFallsBackToNullGasInsteadOfThrowing() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(
                        eventsOnlySample("2026-01-01T10:00:00.000+00:00", 9),
                        depthSample("2026-01-01T10:00:10.000+00:00", 5.0, null, null, null));

        final var profile =
                service.getDiveProfile(
                        COMPUTER,
                        header,
                        samples,
                        List.of(ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas.AIR));

        assertThat(profile.measurements().getFirst().gas()).isNull();
    }

    @Test
    void noGasSwitchEventEverKeepsGasIndexZero() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, null, null, null));

        final var profile =
                service.getDiveProfile(
                        COMPUTER,
                        header,
                        samples,
                        List.of(ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas.AIR));

        assertThat(profile.measurements().getFirst().gas())
                .isEqualTo(ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas.AIR);
    }

    @Test
    void positiveCeilingBecomesADecoStop() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 20.0, null, 4.5, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        final var deco = Objects.requireNonNull(profile.measurements().getFirst().deco());
        assertThat(deco).hasSize(1);
        assertThat(deco.getFirst().depth()).isEqualTo(4.5);
    }

    @Test
    void decoStopSecondsComesFromTheSamplesOwnTimeToSurfaceNotAGuess() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 20.0, null, 4.5, null, 532L));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        final var deco = Objects.requireNonNull(profile.measurements().getFirst().deco());
        assertThat(deco.getFirst().seconds()).isEqualTo(532L);
    }

    @Test
    void missingTimeToSurfaceOnACeilingSampleFallsBackToZeroSecondsRatherThanThrowing() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 20.0, null, 4.5, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        final var deco = Objects.requireNonNull(profile.measurements().getFirst().deco());
        assertThat(deco.getFirst().seconds()).isEqualTo(0L);
    }

    @Test
    void timeToSurfaceIsPopulatedOnEveryMeasurementNotJustCeilingOnes() {
        // A plain ascent with no mandatory deco still has a non-zero TimeToSurface (device-assumed
        // ascent rate) - TTS != 0 does not imply Deco != null.
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 3.0, null, 0.0, null, 20L));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        final var measurement = profile.measurements().getFirst();
        assertThat(measurement.deco()).isEmpty();
        assertThat(measurement.timeToSurface()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void zeroOrNullCeilingProducesNoDecoStop() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 20.0, null, 0.0, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        assertThat(profile.measurements().getFirst().deco()).isEmpty();
    }

    @Test
    void noDecTimeMapsToADurationInSeconds() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, null, null, 6000L));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        assertThat(profile.measurements().getFirst().ndl()).isEqualTo(Duration.ofSeconds(6000));
    }

    @Test
    void missingNoDecTimeLeavesNdlNull() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, null, null, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        assertThat(profile.measurements().getFirst().ndl()).isNull();
    }

    @Test
    void temperatureIsKeptAsKelvinNotPreConvertedToCelsius() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, 297.25, null, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        final var temperature = profile.measurements().getFirst().temperature();
        assertThat(temperature).isNotNull();
        final var t = Objects.requireNonNull(temperature);
        assertThat(t.unit()).isEqualTo(Temperature.TemperatureUnit.KELVIN);
        assertThat(t.value()).isEqualTo(297.25);
        // Existing Temperature.celsius() conversion logic (not re-implemented here) does the
        // actual unit math - this just confirms the raw value/unit pair is passed through intact.
        assertThat(t.celsius()).isCloseTo(24.1, Offset.offset(0.01));
    }

    @Test
    void missingTemperatureLeavesTemperatureNull() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, null, null, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        assertThat(profile.measurements().getFirst().temperature()).isNull();
    }

    @Test
    void diveModeIsAlwaysNullSinceThisIsAnOcOnlyDeviceFormat() {
        final var header =
                new SuuntoHeader(
                        "2026-01-01T10:00:00.000+00:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        20.0);
        final var samples =
                List.of(depthSample("2026-01-01T10:00:00.000+00:00", 5.0, null, null, null));

        final var profile = service.getDiveProfile(COMPUTER, header, samples, List.of());

        assertThat(profile.measurements().getFirst().mode()).isNull();
    }

    @Test
    void startAndEndAreDerivedFromHeaderDateTimeAndDuration() {
        final var header =
                new SuuntoHeader(
                        "2026-08-22T10:13:39.270+02:00",
                        null,
                        new SuuntoDevice("EON Core", "1"),
                        null,
                        3890.0);

        final var profile = service.getDiveProfile(COMPUTER, header, List.of(), List.of());

        assertThat(profile.start()).isEqualTo(Instant.parse("2026-08-22T08:13:39.270Z"));
        assertThat(profile.end()).isEqualTo(Instant.parse("2026-08-22T09:18:29.270Z"));
    }

    // --- parse() (end-to-end, real fixture) --------------------------------------------------

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

    private static final String FIXTURE = "suunto-eon-core-dive-1-deco.json";

    private static ch.sthomas.stddivelogger.service.importer.ParsedImport parseFixture()
            throws IOException {
        final var service = new SuuntoJsonReaderService(diveServiceReturningComputer());
        try (final var inputStream =
                SuuntoJsonReaderServiceTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            final var bytes =
                    Objects.requireNonNull(inputStream, "fixture not found: " + FIXTURE)
                            .readAllBytes();
            return service.parse(
                    new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                    FIXTURE,
                    bytes);
        }
    }

    @Test
    void parsesTheRealFixtureWithoutThrowing() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles()).hasSize(1);
    }

    @Test
    void tagsTheImportAsJsonSuunto() throws IOException {
        assertThat(parseFixture().source()).isEqualTo(PendingImportSource.JSON_SUUNTO);
    }

    @Test
    void usesTheRealAnonymizedDeviceSerialFromTheFixture() throws IOException {
        // "1000000042" is a placeholder substituted for the device's real serial number before
        // this fixture was committed - see CHANGELOG.md for the anonymization note.
        assertThat(parseFixture().computerSerial()).isEqualTo("1000000042");
    }

    @Test
    void startDateAndDurationMatchTheFixtureHeader() throws IOException {
        final var result = parseFixture();
        assertThat(result.startDate()).isEqualTo(Instant.parse("2026-08-22T08:13:39.270Z"));
        assertThat(result.durationSeconds()).isEqualTo(3890L);
    }

    @Test
    void maxDepthGuessComesFromTheHeaderSummaryNotRecomputedFromSamples() throws IOException {
        // Deliberately the device's own quick summary (matches Divesoft's approach for this same
        // "guess" preview field) - the real per-sample depths are still what the saved profile
        // itself is built from, this is only the pending-import preview value.
        assertThat(parseFixture().maxDepth()).isEqualTo(41.65);
    }

    // --- Two-dive TTS baseline (dive-1-deco: real ~8.9min deco; dive-2-nodeco: ~3.1min, no real
    // deco) - see FitReaderService's Suunto test class for the FIT-side half of this comparison.

    private static ch.sthomas.stddivelogger.service.importer.ParsedImport parseNamedFixture(
            final String fixture) throws IOException {
        final var service = new SuuntoJsonReaderService(diveServiceReturningComputer());
        try (final var inputStream =
                SuuntoJsonReaderServiceTest.class.getClassLoader().getResourceAsStream(fixture)) {
            final var bytes =
                    Objects.requireNonNull(inputStream, "fixture not found: " + fixture)
                            .readAllBytes();
            return service.parse(
                    new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                    fixture,
                    bytes);
        }
    }

    private static long maxTimeToSurfaceSeconds(
            final ch.sthomas.stddivelogger.service.importer.ParsedImport result) {
        return result.payload().profiles().getFirst().measurements().stream()
                .map(m -> m.timeToSurface())
                .filter(Objects::nonNull)
                .mapToLong(Duration::toSeconds)
                .max()
                .orElseThrow();
    }

    @Test
    void dive1RealFixturePeaksAtAbout8Point9MinutesOfTimeToSurfaceARealDecoObligation()
            throws IOException {
        final var result = parseNamedFixture("suunto-eon-core-dive-1-deco.json");
        assertThat(maxTimeToSurfaceSeconds(result)).isEqualTo(532L);
    }

    @Test
    void dive2RealFixturePeaksAtOnlyAbout3Point1MinutesOfTimeToSurfaceNoRealDeco()
            throws IOException {
        final var result = parseNamedFixture("suunto-eon-core-dive-2-nodeco.json");
        assertThat(maxTimeToSurfaceSeconds(result)).isEqualTo(188L);
    }

    @Test
    void profileHasOneMeasurementPerDepthBearingSampleExcludingEventsOnlyMarkers()
            throws IOException {
        final var result = parseFixture();
        final var profile = result.payload().profiles().getFirst();
        // The fixture has 404 total Samples entries; 14 of them have no Depth field at all (not
        // just the very first one - the format scatters standalone notification/warning/state
        // markers like "Safety Stop Ahead"/"Deco Window"/"Gas Available" throughout the dive at
        // their own irregular timestamps, confirmed by inspecting the raw fixture directly) and
        // produce no measurement, only a possible gas-index update.
        assertThat(profile.measurements()).hasSize(390);
    }

    @Test
    void everyMeasurementUsesAirSinceTheOnlyGasSwitchEventSwitchesOntoItAtTheStart()
            throws IOException {
        final var result = parseFixture();
        final var profile = result.payload().profiles().getFirst();
        assertThat(profile.measurements())
                .allSatisfy(
                        m -> {
                            final var gas = m.gas();
                            assertThat(gas).isNotNull();
                            assertThat(Objects.requireNonNull(gas).o2())
                                    .isCloseTo(0.21, Offset.offset(0.001));
                        });
    }

    @Test
    void everyMeasurementHasAPlausibleCelsiusTemperatureOnceConverted() throws IOException {
        final var result = parseFixture();
        final var profile = result.payload().profiles().getFirst();
        // This dive has a real thermocline (warm ~24C surface down to ~6C at depth, confirmed by
        // inspecting the raw fixture directly) - the bound below is deliberately wide to cover
        // that, not a sign the conversion is imprecise.
        assertThat(profile.measurements())
                .allSatisfy(
                        m -> {
                            final var t = m.temperature();
                            assertThat(t).isNotNull();
                            final var celsius = Objects.requireNonNull(t).celsius();
                            assertThat(celsius).isBetween(0.0, 30.0);
                        });
    }

    @Test
    void aMalformedDeviceWithNoSerialNumberFailsClearlyInsteadOfSilentlyResolvingAComputer() {
        // Deliberately omits Device.SerialNumber.
        final var json =
                """
                {"DeviceLog":{"Header":{"DateTime":"2026-01-01T10:00:00.000+00:00",
                "Device":{"Name":"EON Core"},"Duration":20.0},"Samples":[]}}
                """;

        assertThatThrownBy(
                        () ->
                                service.parse(
                                        new User(
                                                0,
                                                "",
                                                "",
                                                "",
                                                true,
                                                Instant.now(),
                                                Instant.now(),
                                                null,
                                                null),
                                        "test.json",
                                        json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no serial number");
    }

    // Brand-detection ("is this even a Suunto export") lives in JsonReaderService now - see
    // JsonReaderServiceTest.
}
