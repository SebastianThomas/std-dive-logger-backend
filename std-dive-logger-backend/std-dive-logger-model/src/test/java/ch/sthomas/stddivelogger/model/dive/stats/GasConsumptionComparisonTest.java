package ch.sthomas.stddivelogger.model.dive.stats;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

class GasConsumptionComparisonTest {

    private static final long THIRTY_MIN = 30 * 60;

    private static CylinderConsumptionResult calculated(
            @Nullable final Double ocRmv, @Nullable final Double ocConsumed) {
        return new CylinderConsumptionResult(
                ocRmv, null, null, null, ocConsumed, ocConsumed == null ? null : 100.0, List.of());
    }

    private static GasConsumptionComparison of(
            final DiveGasConsumption gas,
            final CylinderConsumptionResult calc,
            @Nullable final Double avgDepth) {
        return GasConsumptionComparison.of(gas, calc, avgDepth, THIRTY_MIN);
    }

    @Test
    void noMismatchWhenInsertedAndCalculatedRmvAgreeWithinTolerance() {
        final var result = of(new DiveGasConsumption(0, 15.0, 0), calculated(16.0, 1600.0), 20.0);

        assertThat(result.mismatch()).isFalse();
        assertThat(result.insertedRmvLiters()).isEqualTo(15.0);
        assertThat(result.calculatedRmvLiters()).isEqualTo(16.0);
        assertThat(result.calculatedTotalLiters()).isEqualTo(1600.0);
    }

    @Test
    void mismatchWhenInsertedAndCalculatedRmvDifferByMoreThan15Percent() {
        final var result = of(new DiveGasConsumption(0, 12.0, 0), calculated(20.0, 2000.0), 20.0);

        assertThat(result.mismatch()).isTrue();
        assertThat(result.rmvVsCalculatedMismatch()).isTrue();
        assertThat(result.totalLitersMismatch()).isFalse();
    }

    @Test
    void mismatchWhenInsertedAndCalculatedTotalLitresDifferByMoreThan15Percent() {
        // RMV agrees, total litres don't - the total-litres check catches it on its own.
        final var result =
                of(new DiveGasConsumption(0, 16.0, 900.0), calculated(16.0, 1600.0), 20.0);

        assertThat(result.mismatch()).isTrue();
        assertThat(result.rmvVsCalculatedMismatch()).isFalse();
        assertThat(result.totalLitersMismatch()).isTrue();
    }

    @Test
    void oneSideAbsentIsNeverAMismatch() {
        final var result =
                of(new DiveGasConsumption(0, 12.0, 0), CylinderConsumptionResult.EMPTY, null);

        assertThat(result.mismatch()).isFalse();
        assertThat(result.insertedRmvLiters()).isEqualTo(12.0);
        assertThat(result.calculatedRmvLiters()).isNull();
        assertThat(result.impliedRmvFromTotalLiters()).isNull();
    }

    @Test
    void noNpeWhenBothManualInputsAreClearedAndNothingCanBeImplied() {
        // User removed both the RMV and total-litres entries (sacBar may linger). No total -> no
        // implied RMV; the inserted-RMV ternary must not unbox the null impliedRmv.
        final var result = of(new DiveGasConsumption(0.6, 0, 0), calculated(15.0, 1500.0), 20.0);

        assertThat(result.insertedRmvLiters()).isNull();
        assertThat(result.insertedTotalLiters()).isNull();
        assertThat(result.impliedRmvFromTotalLiters()).isNull();
        assertThat(result.mismatch()).isFalse();
        assertThat(result.calculatedRmvLiters()).isEqualTo(15.0);
    }

    @Test
    void derivesInsertedRmvFromTotalLitresWhenNoRmvGiven() {
        // total 1800 L, avg depth 10 m -> 2.0 ata, 30 min -> 1800 / (2.0 * 30) = 30 L/min.
        final var result = of(new DiveGasConsumption(0, 0, 1800.0), calculated(31.0, 1850.0), 10.0);

        assertThat(result.impliedRmvFromTotalLiters()).isEqualTo(30.0);
        assertThat(result.insertedRmvLiters()).isEqualTo(30.0);
        assertThat(result.mismatch()).isFalse();
    }

    @Test
    void flagsAnInternalInsertedRmvVersusTotalInconsistencyWhenNoCylinders() {
        // rmv says 12, total 1800 L over 2.0 ata * 30 min implies 30 - internally inconsistent, and
        // the only check available with no tracked cylinders.
        final var result =
                of(new DiveGasConsumption(0, 12.0, 1800.0), CylinderConsumptionResult.EMPTY, 10.0);

        assertThat(result.insertedRmvLiters()).isEqualTo(12.0);
        assertThat(result.impliedRmvFromTotalLiters()).isEqualTo(30.0);
        assertThat(result.rmvVsImpliedMismatch()).isTrue();
        assertThat(result.mismatch()).isTrue();
    }

    @Test
    void carriesTheDepthDurationAndPressureMinutesForTheBreakdown() {
        final var result =
                of(new DiveGasConsumption(0, 16.0, 1600.0), calculated(16.0, 1600.0), 20.0);

        assertThat(result.avgDepthMeters()).isEqualTo(20.0);
        assertThat(result.durationMinutes()).isEqualTo(30.0);
        assertThat(result.ocPressureMinutes()).isEqualTo(100.0);
    }
}
