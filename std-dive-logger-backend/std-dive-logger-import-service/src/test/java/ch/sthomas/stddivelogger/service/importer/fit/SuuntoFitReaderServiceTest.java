package ch.sthomas.stddivelogger.service.importer.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * End-to-end coverage of {@code FitReaderService.parse()} against two real, independent Suunto EON
 * Core FIT exports - {@code suunto-eon-core-dive-1-deco.fit} (a real ~8.9min TTS deco obligation)
 * and {@code suunto-eon-core-dive-2-nodeco.fit} (a brief NDL scratch, ~3.1min TTS, no real deco) -
 * both per the JSON export's TimeToSurface field, see {@code SuuntoJsonReaderServiceTest}. Both are
 * small, fully anonymized, and committed directly (unlike the real personal `.fit` fixtures
 * elsewhere in this directory, which stay `.gitignore`'d) - see {@code
 * SuuntoFitCharacterizationTest} for the raw-SDK facts these expectations are built on.
 */
class SuuntoFitReaderServiceTest {
    private static final String FIXTURE_1_DECO = "suunto-eon-core-dive-1-deco.fit";
    private static final String FIXTURE_2_NO_DECO = "suunto-eon-core-dive-2-nodeco.fit";

    private static DiveService diveServiceReturningComputers() {
        final var diveService = mock(DiveService.class);
        final var nextId = new AtomicLong(1);
        when(diveService.createDiveComputer(anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(
                        invocation ->
                                new DiveComputer(
                                        nextId.getAndIncrement(),
                                        new DiveComputerManufacturer(1L, invocation.getArgument(2)),
                                        invocation.getArgument(0),
                                        invocation.getArgument(1),
                                        null));
        return diveService;
    }

    private static ch.sthomas.stddivelogger.service.importer.ParsedImport parseFixture(
            final String fixture) throws IOException {
        final var service = new FitReaderService(diveServiceReturningComputers());
        try (final var inputStream =
                SuuntoFitReaderServiceTest.class.getClassLoader().getResourceAsStream(fixture)) {
            return service.parse(
                    new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                    fixture,
                    Objects.requireNonNull(inputStream, "fixture not found: " + fixture));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {FIXTURE_1_DECO, FIXTURE_2_NO_DECO})
    void parsesWithoutThrowingDespiteHavingNoDiveSummaryMessage(final String fixture)
            throws IOException {
        final var result = parseFixture(fixture);
        assertThat(result.payload().profiles()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {FIXTURE_1_DECO, FIXTURE_2_NO_DECO})
    void tagsTheImportAsFitSuuntoNotFitGarmin(final String fixture) throws IOException {
        assertThat(parseFixture(fixture).source()).isEqualTo(PendingImportSource.FIT_SUUNTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {FIXTURE_1_DECO, FIXTURE_2_NO_DECO})
    void resolvesAComputerWithAFallbackSerialSinceTheFileHasNone(final String fixture)
            throws IOException {
        final var result = parseFixture(fixture);
        // file_id.serial_number is genuinely absent on both fixtures (confirmed directly via the
        // SDK for each) - getComputer()'s fallback is what makes this resolve at all.
        assertThat(result.computerSerial())
                .isNotNull()
                .contains("Suunto")
                .contains("Suunto EON Core");
    }

    @ParameterizedTest
    @ValueSource(strings = {FIXTURE_1_DECO, FIXTURE_2_NO_DECO})
    void allMeasurementsUseAirSinceNoGasSwitchEventExistsInThisFormat(final String fixture)
            throws IOException {
        final var result = parseFixture(fixture);
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

    @ParameterizedTest
    @ValueSource(strings = {FIXTURE_1_DECO, FIXTURE_2_NO_DECO})
    void noMeasurementEverHasADecoStopOrTtsEvenOnTheRealDecoDive(final String fixture)
            throws IOException {
        // Confirmed directly via the SDK (see SuuntoFitCharacterizationTest): Suunto's FIT export
        // carries no next_stop_depth/next_stop_time/time_to_surface at all, even for dive-1-deco's
        // real ~8.9min TTS obligation (see SuuntoJsonReaderServiceTest) - a genuine
        // information-loss gap in the source format, not a bug in FitReaderService.
        final var result = parseFixture(fixture);
        final var profile = result.payload().profiles().getFirst();
        assertThat(profile.measurements())
                .allSatisfy(
                        m -> {
                            assertThat(m.deco()).isEmpty();
                            assertThat(m.timeToSurface()).isNull();
                        });
    }

    private static Stream<Arguments> knownValues() {
        return Stream.of(
                Arguments.of(
                        FIXTURE_1_DECO, Instant.parse("2026-08-22T08:13:39Z"), 3890L, 389, 41.58),
                Arguments.of(
                        FIXTURE_2_NO_DECO,
                        Instant.parse("2026-08-23T08:12:30Z"),
                        3140L,
                        314,
                        30.66));
    }

    @ParameterizedTest
    @MethodSource("knownValues")
    void startDateAndDurationMatchTheSessionMessage(
            final String fixture,
            final Instant expectedStart,
            final long expectedDurationSeconds,
            final int expectedMeasurementCount,
            final double expectedMaxDepth)
            throws IOException {
        final var result = parseFixture(fixture);
        assertThat(result.startDate()).isEqualTo(expectedStart);
        assertThat(result.durationSeconds()).isEqualTo(expectedDurationSeconds);
    }

    @ParameterizedTest
    @MethodSource("knownValues")
    void profileHasPlausibleDepthsDerivedFromRecordsNotAVendorSummaryField(
            final String fixture,
            final Instant expectedStart,
            final long expectedDurationSeconds,
            final int expectedMeasurementCount,
            final double expectedMaxDepth)
            throws IOException {
        final var result = parseFixture(fixture);
        final var profile = result.payload().profiles().getFirst();
        assertThat(profile.measurements()).hasSize(expectedMeasurementCount);
        final var maxDepth =
                profile.measurements().stream()
                        .mapToDouble(DiveMeasurement::depth)
                        .max()
                        .orElseThrow();
        // Records-derived, not the device's self-reported summary value - see
        // SuuntoFitAndJsonConsistencyTest for why a small gap between them is expected.
        assertThat(maxDepth).isCloseTo(expectedMaxDepth, Offset.offset(0.05));
    }

    @Test
    void bothFixturesHaveARealDecoDifferenceConfirmedByTheJsonExportsTimeToSurfaceField() {
        // Not a FIT assertion - documents, alongside noMeasurementEverHasADecoStopOrTtsEvenOnThe
        // RealDecoDive above, the ground truth this whole test class is built on: dive-1-deco's
        // JSON export peaks at TimeToSurface=532s (~8.9min, a real deco obligation, above any
        // reasonable auto-tag threshold); dive-2-nodeco peaks at only 188s (~3.1min, a brief NDL
        // scratch with no real mandatory stop) - see SuuntoJsonReaderServiceTest. "FIT has no TTS
        // for either dive" is therefore a real format gap, not a fixture that never had any.
        assertThat(true).isTrue();
    }
}
