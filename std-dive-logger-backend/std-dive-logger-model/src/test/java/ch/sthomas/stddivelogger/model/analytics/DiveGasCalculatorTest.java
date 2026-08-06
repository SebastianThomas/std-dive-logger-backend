package ch.sthomas.stddivelogger.model.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DiveGasCalculatorTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static long nextId = 1;

    private static DiveComputer computer(final long id, final String name) {
        return new DiveComputer(id, new DiveComputerManufacturer(1, "Test"), "SN", name, null);
    }

    private static DiveMeasurementWithId sample(
            final int offsetSeconds,
            final double depth,
            @Nullable final Gas gas,
            @Nullable final PO2 po2,
            @Nullable final DiveMode mode) {
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        START.plusSeconds(offsetSeconds),
                        null,
                        depth,
                        null,
                        List.of(),
                        gas,
                        po2,
                        null,
                        null,
                        null,
                        null,
                        mode),
                nextId++);
    }

    private static DiveProfile profile(
            final long id, final String name, final List<DiveMeasurementWithId> measurements) {
        return new DiveProfile(
                id,
                computer(id, name),
                measurements.getFirst().measurement().time(),
                measurements.getLast().measurement().time(),
                measurements,
                true);
    }

    private static Map<Long, DiveGasCalculator.GasResult> byMeasurementId(
            final List<DiveGasCalculator.GasResult> results) {
        final var map = new java.util.HashMap<Long, DiveGasCalculator.GasResult>();
        results.forEach(r -> map.put(r.measurementId(), r));
        return map;
    }

    private static DiveGasCalculator.GasResult at(
            final Map<Long, DiveGasCalculator.GasResult> results, final DiveMeasurementWithId m) {
        return java.util.Objects.requireNonNull(
                results.get(m.id()), "no result for measurement " + m.id());
    }

    @Test
    void pureOcDiveComputesPo2FromCurrentGasAndDepth() {
        final var air = new Gas(0.21);
        final var m0 = sample(0, 0, air, null, null);
        final var m1 = sample(60, 20, air, null, null); // 20m -> ambient 3.0 bar
        final var profile = profile(1, "OC computer", List.of(m0, m1));

        final var results = byMeasurementId(DiveGasCalculator.calculate(List.of(profile)));

        assertEquals(0.21 * 1.0, at(results, m0).po2(), 1e-9);
        assertEquals(0.21, at(results, m0).fo2(), 1e-9);
        assertEquals(0.21 * 3.0, at(results, m1).po2(), 1e-9);
        assertEquals(0.21, at(results, m1).fo2(), 1e-9);
    }

    @Test
    void gasSwitchIsHeldUntilTheNextSwitchNotInterpolated() {
        final var air = new Gas(0.21);
        final var ean50 = new Gas(0.50);
        final var m0 = sample(0, 0, air, null, null);
        final var m1 = sample(60, 6, null, null, null); // no new gas logged - still on air
        final var m2 = sample(120, 6, ean50, null, null); // switches to EAN50
        final var m3 = sample(180, 6, null, null, null); // still on EAN50
        final var profile = profile(1, "OC computer", List.of(m0, m1, m2, m3));

        final var results = byMeasurementId(DiveGasCalculator.calculate(List.of(profile)));

        final var ambientAt6m = 1.6;
        assertEquals(0.21 * ambientAt6m, at(results, m1).po2(), 1e-9);
        assertEquals(0.50 * ambientAt6m, at(results, m2).po2(), 1e-9);
        assertEquals(0.50 * ambientAt6m, at(results, m3).po2(), 1e-9);
    }

    @Test
    void ccModeUsesOwnMeasuredPo2WhenAvailable() {
        final var m0 = sample(0, 20, null, new PO2(1.2, 1.19, null), DiveMode.CC);
        final var profile = profile(1, "Handset", List.of(m0));

        final var results = byMeasurationOrThrow(DiveGasCalculator.calculate(List.of(profile)), m0);

        assertEquals(1.19, results.po2(), 1e-9);
    }

    private static DiveGasCalculator.GasResult byMeasurationOrThrow(
            final List<DiveGasCalculator.GasResult> results, final DiveMeasurementWithId m) {
        return results.stream().filter(r -> r.measurementId() == m.id()).findFirst().orElseThrow();
    }

    @Test
    void ccModeFallsBackToHandsetSetpointWhenNoMeasuredSensor() {
        // No PO2 sensor at all - only a setpoint.
        final var m0 = sample(0, 20, null, new PO2(1.3, null, null), DiveMode.CC);
        final var m1 = sample(60, 20, null, null, DiveMode.CC); // setpoint held from m0
        final var profile = profile(1, "Handset", List.of(m0, m1));

        final var results = byMeasurementId(DiveGasCalculator.calculate(List.of(profile)));

        assertEquals(1.3, at(results, m0).po2(), 1e-9);
        assertEquals(1.3, at(results, m1).po2(), 1e-9);
    }

    @Test
    void bailoutSwitchesEveryProfileToGasBasedCalculationNotJustTheHandset() {
        // Handset: CC with a setpoint, then bails to OC (no more setpoint, mode flips).
        final var handsetCc = sample(0, 20, null, new PO2(1.3, null, null), DiveMode.CC);
        final var handsetBailout = sample(60, 20, null, null, DiveMode.OC);
        final var handset = profile(1, "Handset", List.of(handsetCc, handsetBailout));

        // Bailout computer: pure OC gas data throughout, never reports mode itself.
        final var bailoutGas = new Gas(0.32);
        final var bailoutBeforeSwitch = sample(0, 20, bailoutGas, null, null);
        final var bailoutAfterSwitch = sample(60, 20, bailoutGas, null, null);
        final var bailoutComputer =
                profile(2, "Bailout", List.of(bailoutBeforeSwitch, bailoutAfterSwitch));

        final var results =
                byMeasurementId(DiveGasCalculator.calculate(List.of(handset, bailoutComputer)));

        final var ambientAt20m = 3.0;
        // Before the bailout: both profiles are CC (per the handset's mode), and the handset's
        // setpoint is available - measured PO2/setpoint always outranks gas-based computation in
        // the CC chain, so *both* profiles read the handset's setpoint here, including the
        // bailout computer's own measurement even though it has perfectly good gas data too.
        assertEquals(1.3, at(results, handsetCc).po2(), 1e-9);
        assertEquals(1.3, at(results, bailoutBeforeSwitch).po2(), 1e-9);

        // After the bailout (mode flips to OC on the handset): every profile now computes from
        // gas. The handset itself has no gas data logged, so it borrows the bailout computer's.
        assertEquals(0.32 * ambientAt20m, at(results, handsetBailout).po2(), 1e-9);
        assertEquals(0.32 * ambientAt20m, at(results, bailoutAfterSwitch).po2(), 1e-9);
    }

    @Test
    void noModeDataAnywhereTreatsTheWholeDiveAsOc() {
        final var air = new Gas(0.21);
        final var m0 = sample(0, 10, air, null, null);
        final var profile = profile(1, "Computer", List.of(m0));

        final var result = byMeasurationOrThrow(DiveGasCalculator.calculate(List.of(profile)), m0);

        assertEquals(0.21 * 2.0, result.po2(), 1e-9);
    }

    @Test
    void aMeasurementWithNoGasDataAnywhereInTheDiveIsLeftOutRatherThanFabricated() {
        final var m0 = sample(0, 10, null, null, null);
        final var profile = profile(1, "Computer", List.of(m0));

        final var results = DiveGasCalculator.calculate(List.of(profile));

        assertTrue(results.isEmpty());
    }

    @Test
    void ccWithNeitherMeasuredNorSetpointFallsBackToGasAsALastResort() {
        final var gas = new Gas(0.21);
        // "Handset" has mode data (so the dive is CC) but never reports measured or setpoint -
        // e.g. a data-sparse dive - while another profile has gas.
        final var handsetSample = sample(0, 20, null, null, DiveMode.CC);
        final var handset = profile(1, "Handset", List.of(handsetSample));
        final var bailoutSample = sample(0, 20, gas, null, null);
        final var bailout = profile(2, "Bailout", new ArrayList<>(List.of(bailoutSample)));

        final var result =
                byMeasurationOrThrow(
                        DiveGasCalculator.calculate(List.of(handset, bailout)), handsetSample);

        assertEquals(0.21 * 3.0, result.po2(), 1e-9);
    }

    @Test
    void fo2IsAlwaysDerivedFromPo2AndAmbientPressure() {
        final var m0 = sample(0, 30, null, new PO2(1.4, null, null), DiveMode.CC);
        final var profile = profile(1, "Handset", List.of(m0));

        final var result = byMeasurationOrThrow(DiveGasCalculator.calculate(List.of(profile)), m0);

        assertEquals(1.4, result.po2(), 1e-9);
        assertEquals(1.4 / 4.0, result.fo2(), 1e-9);
    }

    @Test
    void skipsAMeasurementWithANonFiniteDepthRatherThanThrowing() {
        final var air = new Gas(0.21);
        final var good0 = sample(0, 0, air, null, null);
        final var badNaN = sample(30, Double.NaN, air, null, null);
        final var good1 = sample(60, 10, air, null, null);
        final var profile = profile(1, "Computer", List.of(good0, badNaN, good1));

        final var results = byMeasurementId(DiveGasCalculator.calculate(List.of(profile)));

        assertEquals(2, results.size());
        assertTrue(results.containsKey(good0.id()));
        assertTrue(results.containsKey(good1.id()));
        assertFalse(results.containsKey(badNaN.id()));
    }

    @Test
    void producesOneResultPerMeasurementWhenDataIsAvailableThroughout() {
        final var air = new Gas(0.21);
        final var measurements =
                List.of(sample(0, 0, air, null, null), sample(30, 10, air, null, null));
        final var profile = profile(1, "Computer", measurements);

        final var results = DiveGasCalculator.calculate(List.of(profile));

        assertEquals(measurements.size(), results.size());
        assertFalse(results.isEmpty());
    }
}
