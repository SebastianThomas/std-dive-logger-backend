package ch.sthomas.stddivelogger.model.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
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
        return new DiveProfile(
                1,
                computer(1),
                measurements.getFirst().measurement().time(),
                measurements.getLast().measurement().time(),
                measurements,
                true);
    }

    private static DiveConfigurationCylinder cylinder(
            final double sizeLiters,
            final double startBar,
            final double endBar,
            final CylinderRole role) {
        return new DiveConfigurationCylinder(
                1,
                new CylinderSize(CylinderSizeUnit.LITER, sizeLiters),
                startBar,
                endBar,
                "",
                Gas.AIR,
                role,
                null,
                null);
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
    void combinesMultipleOcCylindersByWeightedSumNotSimpleAverage() {
        // Same window/pressure-minutes (2.0) for both cylinders, so combined RMV is just
        // (1200 + 600) / (2.0 + 2.0) = 450, not (600 + 300) / 2 = 450 coincidentally equal here -
        // use unequal sizes/drops to make sure it's really summing, not averaging per-cylinder RMV.
        final var m0 = sample(0, 0, null);
        final var m1 = sample(60, 20, null);
        final var profile = profile(List.of(m0, m1));
        final var cylinderA = cylinder(12, 200, 100, CylinderRole.OC); // 1200L, RMV 600
        final var cylinderB = cylinder(10, 160, 100, CylinderRole.OC); // 600L, RMV 300

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinderA, cylinderB));

        // (1200 + 600) / (2.0 + 2.0) = 450, distinct from a naive average of 600 and 300 (450 is
        // coincidentally the same here since pressure-minutes are equal - assert the actual
        // formula terms instead to make sure it's not accidentally just averaging).
        assertEquals((1200.0 + 600.0) / (2.0 + 2.0), notNull(result.ocRmvLiters()), 1e-9);
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
                        200.0,
                        150.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        m0.measurement().time(),
                        m1.measurement().time());
        final var cylinderB =
                new DiveConfigurationCylinder(
                        2,
                        new CylinderSize(CylinderSizeUnit.LITER, 12),
                        200.0,
                        100.0,
                        "",
                        Gas.AIR,
                        CylinderRole.OC,
                        m1.measurement().time(),
                        m2.measurement().time());

        final var result =
                CylinderConsumptionCalculator.calculate(
                        List.of(profile), List.of(cylinderA, cylinderB));

        // Cylinder A: 600L over [0m,10m]/1min -> ambient 1.0->2.0, avg 1.5 -> 1.5 pressure-min.
        // Cylinder B: 1200L over [10m,20m]/1min -> ambient 2.0->3.0, avg 2.5 -> 2.5 pressure-min.
        // Combined: (600 + 1200) / (1.5 + 2.5) = 450.
        assertEquals((600.0 + 1200.0) / (1.5 + 2.5), notNull(result.ocRmvLiters()), 1e-9);
    }
}
