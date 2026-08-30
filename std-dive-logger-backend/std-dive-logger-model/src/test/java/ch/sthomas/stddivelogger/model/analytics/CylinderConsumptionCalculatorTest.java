package ch.sthomas.stddivelogger.model.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class CylinderConsumptionCalculatorTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static long nextId = 1;

    private static double notNull(final @Nullable Double value) {
        return java.util.Objects.requireNonNull(value);
    }

    private static DiveComputer computer(final long id) {
        return new DiveComputer(
                id, new DiveComputerManufacturer(1, "Test"), "SN", "Computer", null);
    }

    private static DiveMeasurementWithId sample(
            final int offsetSeconds, final double depth, @Nullable final DiveMode mode) {
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        START.plusSeconds(offsetSeconds),
                        null,
                        depth,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        mode,
                        null),
                nextId++);
    }

    private static DiveProfile profile(final List<DiveMeasurementWithId> measurements) {
        return profile(1, measurements);
    }

    private static DiveProfile profile(
            final long id, final List<DiveMeasurementWithId> measurements) {
        return new DiveProfile(
                id,
                computer(id),
                measurements.getFirst().measurement().time(),
                measurements.getLast().measurement().time(),
                measurements,
                id == 1);
    }

    private static DiveConfigurationCylinder cylinder(
            final double sizeLiters,
            final double startBar,
            final double endBar,
            final CylinderRole role) {
        return new DiveConfigurationCylinder(
                1,
                new CylinderSize(CylinderSizeUnit.LITER, sizeLiters),
                null,
                startBar,
                endBar,
                "",
                Gas.AIR,
                role,
                List.of());
    }

    private static DiveConfigurationCylinder windowedCylinder(
            final double sizeLiters,
            final double startBar,
            final double endBar,
            final CylinderRole role,
            final List<CylinderUsageWindow> windows) {
        return new DiveConfigurationCylinder(
                nextId++,
                new CylinderSize(CylinderSizeUnit.LITER, sizeLiters),
                null,
                startBar,
                endBar,
                "",
                Gas.AIR,
                role,
                windows);
    }

    @Test
    void returnsEmptyWhenThereAreNoCylinders() {
        final var profile = profile(List.of(sample(0, 10, null)));

        final var result = CylinderConsumptionCalculator.calculate(List.of(profile), List.of());

        assertEquals(CylinderConsumptionResult.EMPTY, result);
    }

    @Test
    void computesWholeDiveRmvForAPlainOcDiveFromPressureMinutes() {
        // 0m -> 20m over 60s: ambient goes 1.0 -> 3.0 bar, average 2.0 bar over 1 minute ->
        // 2.0 pressure-minutes. 12L cylinder, 200->100 bar drop -> 1200L consumed.
        // RMV = 1200 / 2.0 = 600 L/min.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 20, null);
        final var profile = profile(List.of(m0, m1));
        final var cylinder = cylinder(12, 200, 100, CylinderRole.OC);

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(cylinder));

        assertEquals(600.0, notNull(result.ocRmvLiters()), 1e-9);
        assertNull(result.bailoutRmvLiters());
        assertNull(result.o2Liters());
        assertNull(result.diluentLiters());
    }

    @Test
    void combinesSimultaneousDoublesByPressureMinutesUnionNotSum() {
        // Two OC cylinders, both with no usage window - the common "true doubles on one manifold"
        // case, breathed at the same time, not sequentially. They share the exact same window, so
        // the denominator must be that one window's pressure-minutes (2.0), not double-counted as
        // if each cylinder covered its own separate 2.0 minutes of the dive.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 20, null);
        final var profile = profile(List.of(m0, m1));
        final var cylinderA = cylinder(12, 200, 100, CylinderRole.OC); // 1200L
        final var cylinderB = cylinder(10, 160, 100, CylinderRole.OC); // 600L

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinderA, cylinderB));

        // Combined 1800L over the one shared 2.0 pressure-minute window -> 900, i.e. literally the
        // sum of each cylinder's own individual RMV (600 + 300) - correct for gas genuinely drawn
        // simultaneously from both tanks across identical elapsed time.
        assertEquals(1800.0 / 2.0, notNull(result.ocRmvLiters()), 1e-9);
    }

    @Test
    void combinesPartiallyOverlappingWindowsByUnionNotSum() {
        // Cylinder A covers the whole dive (both bounds null), cylinder B's own window is fully
        // inside A's. The union is just A's own full-dive window (2.0 pressure-minutes), not
        // A's 2.0 plus B's (smaller) window summed on top.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(30, 10, null);
        final var m2 = sample(60, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        final var cylinderA = cylinder(12, 200, 100, CylinderRole.OC); // 1200L, whole dive
        final var cylinderB =
                new DiveConfigurationCylinder(
                        2,
                        new CylinderSize(CylinderSizeUnit.LITER, 10),
                        null,
                        160.0,
                        100.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(),
                                        m1.measurement().time()))); // 600L, only the first half

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinderA, cylinderB));

        // Union of [whole dive] and [first half] is just [whole dive] -> 2.0 pressure-minutes
        // (same as the single-cylinder case), litres still sum to 1200 + 600 = 1800.
        assertEquals(1800.0 / 2.0, notNull(result.ocRmvLiters()), 1e-9);
    }

    @Test
    void returnsNullOcRmvWhenTheOnlyCylinderHasNoUsablePressureData() {
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var cylinder = cylinder(12, 100, 100, CylinderRole.OC); // no drop -> no consumption

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(cylinder));

        assertNull(result.ocRmvLiters());
    }

    @Test
    void bailoutRmvOnlyCountsTheOpenCircuitPortionOfACcrDive() {
        // On loop (CC) for the first minute at 20m, then bails to OC for the second minute at
        // 20m. Only the second minute should count towards bailout pressure-minutes.
        final var m0 = sample(0, 20, DiveMode.CC);
        final var m1 = sample(60, 20, DiveMode.OC); // bailout starts here
        final var m2 = sample(120, 20, DiveMode.OC);
        final var profile = profile(List.of(m0, m1, m2));
        // Bailout cylinder: 12L, 200->100 bar -> 1200L consumed.
        final var bailoutCylinder = cylinder(12, 200, 100, CylinderRole.BAILOUT);

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(bailoutCylinder));

        // Only the [m1, m2] segment (both held at OC) counts: 1 minute at constant 20m -> ambient
        // 3.0 bar throughout -> 3.0 pressure-minutes. RMV = 1200 / 3.0 = 400.
        assertNull(result.ocRmvLiters()); // this dive is CCR, not plain OC
        assertEquals(400.0, notNull(result.bailoutRmvLiters()), 1e-9);
    }

    @Test
    void ccGasUsageIsSummedAsPlainLitresNotARate() {
        final var m0 = sample(0, 20, DiveMode.CC);
        final var m1 = sample(60, 20, DiveMode.CC);
        final var profile = profile(List.of(m0, m1));
        final var o2Cylinder = cylinder(3, 200, 150, CylinderRole.O2); // 150L
        final var diluentCylinder = cylinder(12, 200, 180, CylinderRole.DILUENT); // 240L

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(o2Cylinder, diluentCylinder));

        assertEquals(150.0, notNull(result.o2Liters()), 1e-9);
        assertEquals(240.0, notNull(result.diluentLiters()), 1e-9);
        assertNull(result.bailoutRmvLiters()); // no BAILOUT-role cylinder tracked
    }

    @Test
    void respectsAnExplicitUsageWindowWhenMoreThanOneCylinderOfTheSameRoleIsUsed() {
        // Two OC cylinders used back-to-back: first minute on cylinder A (0m -> 10m), second
        // minute on cylinder B (10m -> 20m) - each restricted to its own usage window.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        final var cylinderA =
                new DiveConfigurationCylinder(
                        1,
                        new CylinderSize(CylinderSizeUnit.LITER, 12),
                        null,
                        200.0,
                        150.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time())));
        final var cylinderB =
                new DiveConfigurationCylinder(
                        2,
                        new CylinderSize(CylinderSizeUnit.LITER, 12),
                        null,
                        200.0,
                        100.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        List.of(
                                new CylinderUsageWindow(
                                        m1.measurement().time(), m2.measurement().time())));

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinderA, cylinderB));

        // Cylinder A: 600L over [0m,10m]/1min -> ambient 1.0->2.0, avg 1.5 -> 1.5 pressure-min.
        // Cylinder B: 1200L over [10m,20m]/1min -> ambient 2.0->3.0, avg 2.5 -> 2.5 pressure-min.
        // Combined: (600 + 1200) / (1.5 + 2.5) = 450.
        assertEquals((600.0 + 1200.0) / (1.5 + 2.5), notNull(result.ocRmvLiters()), 1e-9);
    }

    @Test
    void aCuftRatedCylinderIsTreatedAsItsWaterVolumeNotItsFreeGasRating() {
        // 0m -> 20m over 60s: 2.0 pressure-minutes. An "80 cuft" AL80 has ~11 L water volume, so a
        // 150 bar drop is ~1600 L, RMV ~800 - not the ~330 000 L / ~165 000 RMV the old
        // cuft-as-cubic-feet bug produced.
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var al80 =
                new DiveConfigurationCylinder(
                        1,
                        new CylinderSize(CylinderSizeUnit.CUFT, 80),
                        null,
                        200.0,
                        50.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        List.of());

        final var result = CylinderConsumptionCalculator.calculate(List.of(profile), List.of(al80));

        assertThat(notNull(result.ocRmvLiters())).isBetween(700.0, 900.0);
        assertThat(notNull(result.ocConsumedLiters())).isBetween(1400.0, 1800.0);
    }

    @Test
    void exposesOcPressureMinutesAsTheRmvDenominator() {
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var cylinder = cylinder(12, 200, 100, CylinderRole.OC); // 1200 L over 2.0 p-min

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(cylinder));

        assertEquals(2.0, notNull(result.ocPressureMinutes()), 1e-9);
        assertEquals(
                notNull(result.ocConsumedLiters()) / notNull(result.ocPressureMinutes()),
                notNull(result.ocRmvLiters()),
                1e-9);
    }

    @Test
    void populatesOcConsumedLitersFromEveryOcCylinder() {
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var a = cylinder(12, 200, 100, CylinderRole.OC); // 1200L
        final var b = cylinder(10, 200, 140, CylinderRole.OC); // 600L

        final var result = CylinderConsumptionCalculator.calculate(List.of(profile), List.of(a, b));

        assertEquals(1800.0, notNull(result.ocConsumedLiters()), 1e-9);
    }

    @Test
    void twoDisjointWindowsOnOneCylinderWeightOverTheirUnion() {
        // Same profile + expected result as respectsAnExplicitUsageWindow... above, but a single
        // cylinder breathed across two disjoint stretches instead of two cylinder rows.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        // 12L, 200 -> 50 bar -> 1800L, windows = [m0,m1] and [m1,m2] (the whole dive).
        final var cylinder =
                windowedCylinder(
                        12,
                        200,
                        50,
                        CylinderRole.OC,
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time()),
                                new CylinderUsageWindow(
                                        m1.measurement().time(), m2.measurement().time())));

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(cylinder));

        // Union is the whole profile -> 1.5 + 2.5 = 4.0 pressure-minutes. 1800 / 4.0 = 450, the
        // same as the old two-row (600 + 1200) / (1.5 + 2.5) result.
        assertEquals(1800.0 / 4.0, notNull(result.ocRmvLiters()), 1e-9);
    }

    @Test
    void unwindowedCylindersShareTheComplementOfTheWindowedOnes() {
        // 4 one-minute segments: p-minutes 1.5 / 2.5 / 2.5 / 1.5 -> 8.0 total.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var m3 = sample(180, 10, null);
        final var m4 = sample(240, 0, null);
        final var profile = profile(List.of(m0, m1, m2, m3, m4));
        final var a =
                windowedCylinder(
                        12,
                        200,
                        150,
                        CylinderRole.OC, // 600L
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time())));
        final var b =
                windowedCylinder(
                        12,
                        200,
                        100,
                        CylinderRole.OC, // 1200L
                        List.of(
                                new CylinderUsageWindow(
                                        m1.measurement().time(), m2.measurement().time())));
        final var c =
                windowedCylinder(
                        12,
                        200,
                        100,
                        CylinderRole.OC, // 1200L
                        List.of(
                                new CylinderUsageWindow(
                                        m2.measurement().time(), m3.measurement().time())));
        final var d = cylinder(12, 210, 200, CylinderRole.OC); // 120L, unwindowed
        final var e = cylinder(10, 200, 190, CylinderRole.OC); // 100L, unwindowed

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(a, b, c, d, e));

        // The 2 unwindowed cover the last segment (the complement); denominator is the whole dive.
        assertEquals(
                (600.0 + 1200.0 + 1200.0 + 120.0 + 100.0) / 8.0,
                notNull(result.ocRmvLiters()),
                1e-9);
    }

    @Test
    void unwindowedCylinderExcludedWhenWindowedCylindersSpanTheWholeDive() {
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        final var windowed =
                windowedCylinder(
                        12,
                        200,
                        50,
                        CylinderRole.OC, // 1800L over the whole dive
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time()),
                                new CylinderUsageWindow(
                                        m1.measurement().time(), m2.measurement().time())));
        final var unwindowed = cylinder(12, 200, 100, CylinderRole.OC); // 1200L, contradictory

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(windowed, unwindowed));

        // Complement is empty -> the unwindowed cylinder's litres are excluded rather than
        // inflating RMV: 1800 / 4.0 = 450, not (1800 + 1200) / 4.0.
        assertEquals(1800.0 / 4.0, notNull(result.ocRmvLiters()), 1e-9);
    }

    // --- per-cylinder contributions (the "show the working" breakdown) ---

    @Test
    void eachOcCylinderCarriesItsOwnRmvOverItsOwnWindow() {
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        final var bottom =
                windowedCylinder(
                        12,
                        200,
                        150,
                        CylinderRole.OC, // 600L over [0m,10m] -> 1.5 p-min -> RMV 400
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time())));
        final var deco =
                windowedCylinder(
                        7,
                        200,
                        100,
                        CylinderRole.OC, // 700L over [10m,20m] -> 2.5 p-min -> RMV 280
                        List.of(
                                new CylinderUsageWindow(
                                        m1.measurement().time(), m2.measurement().time())));

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(bottom, deco));

        final var contributions = result.contributions();
        assertEquals(2, contributions.size());
        assertEquals(400.0, notNull(contributions.get(0).rmvLiters()), 1e-9);
        assertEquals(1.5, notNull(contributions.get(0).pressureMinutes()), 1e-9);
        assertEquals(280.0, notNull(contributions.get(1).rmvLiters()), 1e-9);
        assertThat(contributions).allSatisfy(c -> assertThat(c.coversWholeDive()).isFalse());
    }

    @Test
    void o2AndDiluentContributionsHaveLitresButNoRmv() {
        final var m0 = sample(0, 20, DiveMode.CC);
        final var m1 = sample(60, 20, DiveMode.CC);
        final var profile = profile(List.of(m0, m1));
        final var o2 = cylinder(3, 200, 150, CylinderRole.O2); // 150L
        final var diluent = cylinder(12, 200, 180, CylinderRole.DILUENT); // 240L

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(o2, diluent));

        final var contributions = result.contributions();
        assertEquals(150.0, notNull(contributions.get(0).consumedLiters()), 1e-9);
        assertNull(contributions.get(0).rmvLiters());
        assertNull(contributions.get(0).pressureMinutes());
        assertNull(contributions.get(1).rmvLiters());
    }

    @Test
    void aLoneUnwindowedOcCylinderCoversTheWholeDive() {
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var cylinder = cylinder(12, 200, 100, CylinderRole.OC);

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(cylinder));

        final var only = result.contributions().getFirst();
        assertThat(only.coversWholeDive()).isTrue();
        assertThat(only.effectiveWindows()).isEmpty();
        assertEquals(notNull(result.ocRmvLiters()), notNull(only.rmvLiters()), 1e-9);
    }

    @Test
    void anUnwindowedCylinderNextToAWindowedOneGetsTheComputedComplementInterval() {
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 10, null);
        final var m2 = sample(120, 20, null);
        final var profile = profile(List.of(m0, m1, m2));
        final var windowed =
                windowedCylinder(
                        12,
                        200,
                        150,
                        CylinderRole.OC,
                        List.of(
                                new CylinderUsageWindow(
                                        m0.measurement().time(), m1.measurement().time())));
        final var unwindowed = cylinder(7, 200, 120, CylinderRole.OC);

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(windowed, unwindowed));

        final var complement = result.contributions().get(1);
        assertThat(complement.coversWholeDive()).isFalse();
        assertEquals(1, complement.effectiveWindows().size());
        assertEquals(m1.measurement().time(), complement.effectiveWindows().getFirst().start());
        assertEquals(m2.measurement().time(), complement.effectiveWindows().getFirst().end());
        assertThat(complement.rmvLiters()).isNotNull();
    }

    @Test
    void bailoutCylinderContributionRmvCountsOnlyTheOpenCircuitSpan() {
        final var m0 = sample(0, 20, DiveMode.CC);
        final var m1 = sample(60, 20, DiveMode.OC);
        final var m2 = sample(120, 20, DiveMode.OC);
        final var profile = profile(List.of(m0, m1, m2));
        final var bailout = cylinder(12, 200, 100, CylinderRole.BAILOUT); // 1200L

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(bailout));

        final var contribution = result.contributions().getFirst();
        assertEquals(400.0, notNull(contribution.rmvLiters()), 1e-9); // 1200 / 3.0 p-min (OC only)
        assertEquals(notNull(result.bailoutRmvLiters()), notNull(contribution.rmvLiters()), 1e-9);
    }

    @Test
    void exposesTheOpenCircuitSpanAndBailoutDenominatorForACcrDive() {
        final var m0 = sample(0, 20, DiveMode.CC);
        final var m1 = sample(60, 20, DiveMode.CC);
        final var m2 = sample(120, 20, DiveMode.OC); // bailout switch here
        final var m3 = sample(180, 20, DiveMode.OC);
        final var profile = profile(List.of(m0, m1, m2, m3));
        final var bailout = cylinder(12, 200, 100, CylinderRole.BAILOUT);

        final var result =
                CylinderConsumptionCalculator.calculate(List.of(profile), List.of(bailout));

        // Only [m2, m3] is open-circuit: 1 minute at 20 m -> 3.0 pressure-minutes.
        assertEquals(3.0, notNull(result.bailoutPressureMinutes()), 1e-9);
        assertEquals(1, result.openCircuitWindows().size());
        assertEquals(m2.measurement().time(), result.openCircuitWindows().getFirst().start());
        assertEquals(m3.measurement().time(), result.openCircuitWindows().getFirst().end());
    }

    @Test
    void coincidentSamplesFromMergedProfilesDoNotFragmentTheOpenCircuitSpan() {
        // A CCR dive whose profile is merged from two computers (the rebreather, which reports
        // mode, + a bailout/deco computer that samples the same whole seconds but reports no
        // mode) - so the combined depth timeline has pairs of identical timestamps. The
        // open-circuit span must stay one interval, not shatter into a touching fragment per
        // collision (the dive-217 bug).
        final var rebreather =
                profile(
                        1,
                        List.of(
                                sample(0, 20, DiveMode.CC),
                                sample(60, 20, DiveMode.OC), // bail out
                                sample(120, 20, DiveMode.OC),
                                sample(180, 20, DiveMode.OC),
                                sample(240, 20, DiveMode.CC))); // back on the loop
        final var decoComputer =
                profile(
                        2,
                        List.of(
                                sample(0, 20, null),
                                sample(60, 20, null),
                                sample(120, 20, null),
                                sample(180, 20, null),
                                sample(240, 20, null)));
        final var bailout = cylinder(12, 200, 100, CylinderRole.BAILOUT);

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(rebreather, decoComputer), List.of(bailout));

        assertEquals(1, result.openCircuitWindows().size());
        assertEquals(START.plusSeconds(60), result.openCircuitWindows().getFirst().start());
        assertEquals(START.plusSeconds(240), result.openCircuitWindows().getFirst().end());
        // 3 minutes open-circuit at a constant 20 m -> 3.0 bar ambient -> 9.0 pressure-minutes.
        assertEquals(9.0, notNull(result.bailoutPressureMinutes()), 1e-9);
    }

    @Test
    void openCircuitSpanIsEmptyOnAPlainOcDive() {
        final var profile = profile(List.of(sample(0, 0, null), sample(60, 20, null)));
        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinder(12, 200, 100, CylinderRole.OC)));

        assertThat(result.openCircuitWindows()).isEmpty();
        assertNull(result.bailoutPressureMinutes());
    }
}
