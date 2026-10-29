package ch.sthomas.stddivegastracker.calc.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.calc.gas.CompressibilityModel;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.IdealGas;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Event;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Inflow;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Outflow;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Reading;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Result;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Vessel;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

final class InventoryReplayTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final double TOLERANCE = 1e-4;
    // §4.3 solves pressure to 1e-6 bar, i.e. about 5e-5 L at Vw = 50
    private static final double ROUND_TRIP_LITRES = 1e-4;

    private static Instant at(final long minutes) {
        return T0.plusSeconds(60 * minutes);
    }

    // --- Bought O2 bank: Vw = 50 L, 200 bar, IdealGas ---
    //
    // IdealGas content = Vw + Vw·p/P_ATM, with K = Vw/P_ATM = 50/1.01325 = 49.34616 L/bar:
    //   open  = 50 + 200·K = 50 + 9869.23267 = 9919.23267 L (= V_in)
    //   pressure of a content C: p = (C/50 − 1)·P_ATM = 200 − (draws so far)·P_ATM/50
    // Draws 3000 / 3000 / 2600 L (incl. line loss 10 / 8 / 6 L), readings 138 bar after the first
    // and 79 bar after the second.

    private static final Vessel BANK = new Vessel(50, GasMix.OXYGEN, 200, 20, null);

    private static List<Event> bankEvents() {
        return List.of(
                new Outflow(1, at(60), 3000, 10, GasMix.OXYGEN),
                new Reading(2, at(120), 138),
                new Outflow(3, at(180), 3000, 8, GasMix.OXYGEN),
                new Reading(4, at(240), 79),
                new Outflow(5, at(300), 2600, 6, GasMix.OXYGEN));
    }

    @Test
    void boughtBankBookContentFlowTotalsAndPressure() {
        final var result = InventoryReplay.replay(BANK, bankEvents(), IdealGas.INSTANCE);

        // book = 9919.23267 − (3000 + 3000 + 2600) = 1319.23267 L, all O2
        assertThat(result.bookContentLitres()).isCloseTo(1319.23267, within(TOLERANCE));
        assertThat(result.bookO2Litres()).isCloseTo(1319.23267, within(TOLERANCE));
        assertThat(result.bookHeLitres()).isZero();
        assertThat(result.bookN2Litres()).isZero();
        assertThat(result.bookMix().o2()).isCloseTo(1.0, within(1e-12));
        assertThat(result.bookMix().he()).isZero();
        // book pressure = 200 − 8600·1.01325/50 = 25.721 bar
        assertThat(result.bookPressureBar()).isCloseTo(25.721, within(1e-5));
        // V_in = open content (no inflow events), D = 8600, line loss = 10 + 8 + 6
        assertThat(result.inflowLitres()).isCloseTo(9919.23267, within(TOLERANCE));
        assertThat(result.drawnLitres()).isEqualTo(8600.0);
        assertThat(result.lineLossLitres()).isEqualTo(24.0);
        assertThat(result.lossReport()).isNull();
    }

    @Test
    void theOpeningContentHasTheRegisteredGasEvenAfterTheDeclaredMixChanged() {
        // registered full of EAN32, then He blended in and analysed: the declared mix is now 28/13
        final var ean32 = new GasMix(0.32, 0);
        final var trimix = new GasMix(0.28, 0.13);
        final var blended = new Vessel(50, trimix, ean32, 200, 20, null);
        final var heLitres = 1300.0;
        final var events = List.<Event>of(new Inflow(1, at(60), 0, heLitres, heLitres));

        final var result = InventoryReplay.replay(blended, events, IdealGas.INSTANCE);

        final var open = GasContent.contentLitres(50, 200, ean32, IdealGas.INSTANCE);
        assertThat(result.inflowLitres()).isCloseTo(open + heLitres, within(TOLERANCE));
        assertThat(result.bookO2Litres()).isCloseTo(open * 0.32, within(TOLERANCE));
        // only the He that was blended in, none from re-reading the opening gas as trimix
        assertThat(result.bookHeLitres()).isCloseTo(heLitres, within(TOLERANCE));
        // the residual is held in the declared (current) mix
        assertThat(result.residualLitres())
                .isCloseTo(
                        GasContent.contentLitres(50, 20, trimix, IdealGas.INSTANCE),
                        within(TOLERANCE));
    }

    @Test
    void discrepancyIsBookMinusMeasuredPositiveWhenGasIsMissing() {
        final var result = InventoryReplay.replay(BANK, bankEvents(), IdealGas.INSTANCE);

        assertThat(result.readings()).hasSize(2);
        final var first = result.readings().get(0);
        // after draw 1: book = 6919.23267 L → 200 − 3000·P_ATM/50 = 139.205 bar
        // measured at 138 bar = 50 + 138·K = 6859.77054 L
        // discrepancy = 6919.23267 − 6859.77054 = +59.46213 L (= 1.205 bar·K): gauge lower than
        // book
        assertThat(first.readingId()).isEqualTo(2);
        assertThat(first.bookContentLitres()).isCloseTo(6919.23267, within(TOLERANCE));
        assertThat(first.bookPressureBar()).isCloseTo(139.205, within(1e-5));
        assertThat(first.discrepancyLitres()).isCloseTo(59.46213, within(TOLERANCE));

        final var second = result.readings().get(1);
        // after draw 2: book = 3919.23267 L → 78.41 bar; measured at 79 bar = 50 + 79·K =
        // 3948.34690
        // discrepancy = 3919.23267 − 3948.34690 = −29.11424 L (= −0.59 bar·K): gauge higher
        assertThat(second.readingId()).isEqualTo(4);
        assertThat(second.bookContentLitres()).isCloseTo(3919.23267, within(TOLERANCE));
        assertThat(second.bookPressureBar()).isCloseTo(78.41, within(1e-5));
        assertThat(second.discrepancyLitres()).isCloseTo(-29.11424, within(TOLERANCE));
    }

    @Test
    void estimateStartsFromTheLastReadingAndUsableContentIsAboveTheResidual() {
        final var result = InventoryReplay.replay(BANK, bankEvents(), IdealGas.INSTANCE);

        // estimate = measured at the last reading + later events = 3948.34690 − 2600 = 1348.34690 L
        // (the book says 1319.23267 L); pressure = 79 − 2600·P_ATM/50 = 26.311 bar
        assertThat(result.estimatedContentLitres()).isCloseTo(1348.34690, within(TOLERANCE));
        assertThat(result.estimatedPressureBar()).isCloseTo(26.311, within(1e-5));
        // V_res at 20 bar O2 = 50 + 20·K = 1036.92327 L; usable = 1348.34690 − 1036.92327
        assertThat(result.residualLitres()).isCloseTo(1036.92327, within(TOLERANCE));
        assertThat(result.usableContentLitres()).isCloseTo(311.42364, within(TOLERANCE));
    }

    @Test
    void lossReportAfterClosingAt25Bar() {
        final var closed = new Vessel(50, GasMix.OXYGEN, 200, 20, 25.0);

        final var report =
                Objects.requireNonNull(
                        InventoryReplay.replay(closed, bankEvents(), IdealGas.INSTANCE)
                                .lossReport());

        assertThat(report.inflowLitres()).isCloseTo(9919.23267, within(TOLERANCE));
        assertThat(report.drawnLitres()).isEqualTo(8600.0);
        assertThat(report.lineLossLitres()).isEqualTo(24.0);
        // residual at 25 bar = 50 + 25·K = 1283.65408 L
        assertThat(report.residualLitres()).isCloseTo(1283.65408, within(TOLERANCE));
        // unaccounted = V_in − D − residual = 9919.23267 − 8600 − 1283.65408 = 35.57859 L
        assertThat(report.unaccountedLitres()).isCloseTo(35.57859, within(TOLERANCE));
        // loss fraction = (V_in − D)/V_in = 1319.23267 / 9919.23267
        assertThat(report.lossFraction()).isCloseTo(0.132997, within(1e-6));
    }

    @Test
    void lossFractionIsZeroWhenNothingFlowedIn() {
        // a degenerate model with infinite Z holds 0 L at every pressure, so V_in = 0
        // checkstyle: explicit-type lambda target
        final CompressibilityModel infiniteZ = (_, _) -> Double.POSITIVE_INFINITY;
        final var closed = new Vessel(50, GasMix.OXYGEN, 200, 20, 25.0);

        final var report =
                Objects.requireNonNull(
                        InventoryReplay.replay(closed, List.of(), infiniteZ).lossReport());

        assertThat(report.inflowLitres()).isZero();
        assertThat(report.lossFraction()).isZero();
    }

    // --- House mix: Vw = 10 L at 0 bar air, IdealGas ---
    //
    // open (0 bar) = Vw = 10 L of air: O2 2.09, He 0, N2 7.91
    // + He 30 L           → He 30
    // + O2 12 L           → O2 14.09                       (total 52 = V_in)
    // − draw 20 L of 27/58 (N2 0.15): O2 5.4, He 11.6, N2 3.0
    // book: O2 8.69, He 18.4, N2 4.91, total 32 → mix 0.2715625 / 0.575 / 0.1534375

    @Test
    void houseMixBookMixAfterTwoInflowsAndOneOutflow() {
        final var house = new Vessel(10, GasMix.AIR, 0, 0, null);
        final var events =
                List.of(
                        new Inflow(1, at(60), 0, 30, 30),
                        new Inflow(2, at(120), 12, 0, 12),
                        new Outflow(3, at(180), 20, 1, new GasMix(0.27, 0.58)));

        final var result = InventoryReplay.replay(house, events, IdealGas.INSTANCE);

        assertThat(result.bookO2Litres()).isCloseTo(8.69, within(1e-9));
        assertThat(result.bookHeLitres()).isCloseTo(18.4, within(1e-9));
        assertThat(result.bookN2Litres()).isCloseTo(4.91, within(1e-9));
        assertThat(result.bookContentLitres()).isCloseTo(32.0, within(1e-9));
        assertThat(result.bookMix().o2()).isCloseTo(0.2715625, within(1e-9));
        assertThat(result.bookMix().he()).isCloseTo(0.575, within(1e-9));
        assertThat(result.bookMix().n2()).isCloseTo(0.1534375, within(1e-9));
        assertThat(result.inflowLitres()).isCloseTo(52.0, within(1e-9));
        assertThat(result.drawnLitres()).isEqualTo(20.0);
        assertThat(result.lineLossLitres()).isEqualTo(1.0);
        // pressure = (32/10 − 1)·P_ATM = 2.22915 bar
        assertThat(result.bookPressureBar()).isCloseTo(2.22915, within(1e-5));
        // no reading: the estimate is the book; V_res at 0 bar = 10 L, usable = 32 − 10
        assertThat(result.readings()).isEmpty();
        assertThat(result.estimatedContentLitres()).isCloseTo(32.0, within(1e-9));
        assertThat(result.estimatedPressureBar()).isCloseTo(result.bookPressureBar(), within(1e-9));
        assertThat(result.residualLitres()).isCloseTo(10.0, within(1e-9));
        assertThat(result.usableContentLitres()).isCloseTo(22.0, within(1e-9));
        assertThat(result.lossReport()).isNull();
    }

    // --- Ordering ---

    private static List<Event> bankEventsWithTopUp() {
        final var events = new ArrayList<Event>(bankEvents());
        events.add(new Inflow(6, at(210), 500, 0, 500));
        return events;
    }

    @Test
    void shuffledEventsGiveTheSameResult() {
        final var expected = InventoryReplay.replay(BANK, bankEventsWithTopUp(), IdealGas.INSTANCE);

        for (long seed = 0; seed < 50; seed++) {
            final var shuffled = new ArrayList<Event>(bankEventsWithTopUp());
            Collections.shuffle(shuffled, new Random(seed));

            assertThat(InventoryReplay.replay(BANK, shuffled, IdealGas.INSTANCE))
                    .as("seed %s", seed)
                    .isEqualTo(expected);
        }
    }

    @Test
    void atTheSameInstantFlowsComeBeforeReadingsWhateverTheIds() {
        // 200-bar bank: a 3000 L draw and a 190-bar reading at the same instant. The reading is
        // the bank gauge after that fill, so the draw is booked first: 9919.23267 - 3000 L.
        final var draw = new Outflow(2, at(60), 3000, 0, GasMix.OXYGEN);
        for (final long readingId : new long[] {1, 3}) {
            final var result =
                    InventoryReplay.replay(
                            BANK,
                            List.of(new Reading(readingId, at(60), 190), draw),
                            IdealGas.INSTANCE);
            assertThat(result.readings().getFirst().bookContentLitres())
                    .as("reading id %s", readingId)
                    .isCloseTo(6919.23267, within(TOLERANCE));
        }
    }

    @Test
    void atTheSameInstantInflowsComeBeforeDrawsAndIdsOrderWithinAKind() {
        // inflow id 9 before outflow id 1 (kind first); two draws by id - book is the same sum
        final var topUp = new Inflow(9, at(60), 500, 0, 500);
        final var first = new Outflow(1, at(60), 100, 0, GasMix.OXYGEN);
        final var second = new Outflow(2, at(60), 200, 0, GasMix.OXYGEN);
        final var result =
                InventoryReplay.replay(
                        BANK,
                        List.of(second, first, topUp, new Reading(4, at(60), 190)),
                        IdealGas.INSTANCE);
        assertThat(result.readings().getFirst().bookContentLitres())
                .isCloseTo(9919.23267 + 500 - 300, within(TOLERANCE));
    }

    @Test
    void implausiblyFullBooksAreCappedInsteadOfFailingTheInventory() {
        // a 10 L bank receives far more gas than fits at 1000 bar (inconsistent records)
        final var tiny = new Vessel(10, GasMix.OXYGEN, 0, 0, null);
        final var result =
                InventoryReplay.replay(
                        tiny,
                        List.of(
                                new Inflow(1, at(10), 1_000_000, 0, 1_000_000),
                                new Reading(2, at(20), 100)),
                        IdealGas.INSTANCE);

        assertThat(result.bookPressureBar()).isEqualTo(GasContent.MAX_PRESSURE_BAR);
        assertThat(result.readings().getFirst().bookPressureBar())
                .isEqualTo(GasContent.MAX_PRESSURE_BAR);
        assertThat(result.estimatedPressureBar()).isLessThan(GasContent.MAX_PRESSURE_BAR);
    }

    // --- Both compressibility models ---

    @Test
    void flowTotalsAreModelIndependentAndPressuresRoundTripPerModel() {
        final var events = bankEventsWithTopUp();
        final var ideal = InventoryReplay.replay(BANK, events, IdealGas.INSTANCE);
        final var virial = InventoryReplay.replay(BANK, events, VirialFit.INSTANCE);

        for (final var entry :
                List.of(
                        new ModelRun(IdealGas.INSTANCE, ideal),
                        new ModelRun(VirialFit.INSTANCE, virial))) {
            final var model = entry.model();
            final var result = entry.result();
            final double openLitres = GasContent.contentLitres(50, 200, GasMix.OXYGEN, model);
            // Σ inflow added = 500, D = 8600, line loss = 24 whatever the model
            assertThat(result.inflowLitres() - openLitres).isCloseTo(500.0, within(1e-9));
            assertThat(result.drawnLitres()).isEqualTo(8600.0);
            assertThat(result.lineLossLitres()).isEqualTo(24.0);
            // book content is positive, so its pressure reproduces it under the same model
            assertThat(result.bookContentLitres()).isPositive();
            assertThat(
                            GasContent.contentLitres(
                                    50, result.bookPressureBar(), result.bookMix(), model))
                    .isCloseTo(result.bookContentLitres(), within(ROUND_TRIP_LITRES));
            assertThat(result.readings()).hasSize(2);
            final double[] readingBar = {138, 79};
            for (int i = 0; i < 2; i++) {
                final var reading = result.readings().get(i);
                assertThat(reading.bookContentLitres()).isPositive();
                assertThat(
                                GasContent.contentLitres(
                                        50, reading.bookPressureBar(), GasMix.OXYGEN, model))
                        .isCloseTo(reading.bookContentLitres(), within(ROUND_TRIP_LITRES));
                // measured = content at the reading pressure under the same model
                assertThat(reading.discrepancyLitres())
                        .isCloseTo(
                                reading.bookContentLitres()
                                        - GasContent.contentLitres(
                                                50, readingBar[i], GasMix.OXYGEN, model),
                                within(1e-9));
            }
        }

        assertThat(virial.inflowLitres()).isNotCloseTo(ideal.inflowLitres(), within(1.0));
        assertThat(virial.bookPressureBar()).isNotCloseTo(ideal.bookPressureBar(), within(0.1));
        assertThat(virial.drawnLitres()).isEqualTo(ideal.drawnLitres());
        assertThat(virial.lineLossLitres()).isEqualTo(ideal.lineLossLitres());
    }

    private record ModelRun(CompressibilityModel model, Result result) {}

    // --- Overdrawn book ---

    @Test
    void overdrawnBookHasPressureZeroAndNoException() {
        // Vw = 10 L of air at 0 bar holds 10 L; drawing 15 L leaves −5 L
        final var vessel = new Vessel(10, GasMix.AIR, 0, 0, null);
        final var events =
                List.of(new Outflow(1, at(60), 15, 0, GasMix.AIR), new Reading(2, at(120), 0));

        final var result = InventoryReplay.replay(vessel, events, IdealGas.INSTANCE);

        // components stay honest: O2 2.09 − 3.135, N2 7.91 − 11.865
        assertThat(result.bookContentLitres()).isCloseTo(-5.0, within(1e-9));
        assertThat(result.bookO2Litres()).isCloseTo(-1.045, within(1e-9));
        assertThat(result.bookN2Litres()).isCloseTo(-3.955, within(1e-9));
        assertThat(result.bookMix()).isEqualTo(GasMix.AIR);
        assertThat(result.bookPressureBar()).isZero();
        assertThat(result.estimatedPressureBar()).isZero();
        assertThat(result.usableContentLitres()).isZero();
        // reading at 0 bar measures Vw = 10 L; book − measured = −5 − 10
        final var reading = result.readings().getFirst();
        assertThat(reading.bookPressureBar()).isZero();
        assertThat(reading.discrepancyLitres()).isCloseTo(-15.0, within(1e-9));
    }

    @Test
    void mixIsBuiltFromThePositiveComponentsOnly() {
        // 10 L of air; a 5 L draw of pure O2 leaves O2 2.09 − 5 = −2.91, N2 7.91 (total 5 L);
        // an air inflow of 10 L (O2 2.09, N2 remainder 7.91) → O2 −0.82, N2 15.82 (total 15 L)
        final var vessel = new Vessel(10, GasMix.AIR, 0, 0, null);
        final var events =
                List.of(
                        new Outflow(1, at(60), 5, 0, GasMix.OXYGEN),
                        new Inflow(2, at(120), 2.09, 0, 10));

        final var result = InventoryReplay.replay(vessel, events, IdealGas.INSTANCE);

        assertThat(result.bookO2Litres()).isCloseTo(-0.82, within(1e-9));
        assertThat(result.bookN2Litres()).isCloseTo(15.82, within(1e-9));
        assertThat(result.bookContentLitres()).isCloseTo(15.0, within(1e-9));
        assertThat(result.bookMix().o2()).isZero();
        assertThat(result.bookMix().he()).isZero();
        // pressure = (15/10 − 1)·P_ATM = 0.506625 bar
        assertThat(result.bookPressureBar()).isCloseTo(0.506625, within(1e-5));
    }

    // --- Validation ---

    @Test
    void rejectsNegativeOrNonFiniteLitres() {
        assertThatThrownBy(() -> new Inflow(1, T0, -1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("O2 litres");
        assertThatThrownBy(() -> new Inflow(1, T0, 0, Double.NaN, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("He litres");
        assertThatThrownBy(() -> new Inflow(1, T0, 0, 0, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total litres");
        assertThatThrownBy(() -> new Outflow(1, T0, -1, 0, GasMix.AIR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Draw litres");
        assertThatThrownBy(() -> new Outflow(1, T0, 1, Double.NaN, GasMix.AIR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Line loss litres");
    }

    @Test
    void rejectsNegativeOrNonFiniteReadingPressure() {
        assertThatThrownBy(() -> new Reading(1, T0, -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reading pressure");
        assertThatThrownBy(() -> new Reading(1, T0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reading pressure");
    }

    @Test
    void rejectsAnInvalidVessel() {
        assertThatThrownBy(
                        () ->
                                InventoryReplay.replay(
                                        new Vessel(0, GasMix.AIR, 0, 0, null),
                                        List.of(),
                                        IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Water volume");
        assertThatThrownBy(
                        () ->
                                InventoryReplay.replay(
                                        new Vessel(10, GasMix.AIR, -1, 0, null),
                                        List.of(),
                                        IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pressure");
        assertThatThrownBy(
                        () ->
                                InventoryReplay.replay(
                                        new Vessel(10, GasMix.AIR, 0, 0, Double.NaN),
                                        List.of(),
                                        IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pressure");
    }
}
