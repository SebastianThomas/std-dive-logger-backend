package ch.sthomas.stddivelogger.model.dive.stats;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GasConsumptionComparisonTest {

    private static final long THIRTY_MIN = 30 * 60;

    private static CylinderConsumptionResult calculated(
            @Nullable final Double ocRmv, @Nullable final Double ocConsumed) {
        return new CylinderConsumptionResult(ocRmv, null, null, null, ocConsumed);
    }

    @Test
    void noMismatchWhenInsertedAndCalculatedRmvAgreeWithinTolerance() {
        final var result =
                GasConsumptionComparison.of(
                        new DiveGasConsumption(0, 15.0, 0),
                        calculated(16.0, 1600.0),
                        20.0,
                        THIRTY_MIN);

        assertThat(result.mismatch()).isFalse();
        assertThat(result.insertedRmvLiters()).isEqualTo(15.0);
        assertThat(result.calculatedRmvLiters()).isEqualTo(16.0);
        assertThat(result.calculatedTotalLiters()).isEqualTo(1600.0);
    }

    @Test
    void mismatchWhenInsertedAndCalculatedRmvDifferByMoreThan15Percent() {
        final var result =
                GasConsumptionComparison.of(
                        new DiveGasConsumption(0, 12.0, 0),
                        calculated(20.0, 2000.0),
                        20.0,
                        THIRTY_MIN);

        assertThat(result.mismatch()).isTrue();
    }

    @Test
    void oneSideAbsentIsNeverAMismatch() {
        final var result =
                GasConsumptionComparison.of(
                        new DiveGasConsumption(0, 12.0, 0),
                        CylinderConsumptionResult.EMPTY,
                        null,
                        THIRTY_MIN);

        assertThat(result.mismatch()).isFalse();
        assertThat(result.insertedRmvLiters()).isEqualTo(12.0);
        assertThat(result.calculatedRmvLiters()).isNull();
        assertThat(result.impliedRmvFromTotalLiters()).isNull();
    }

    @Test
    void derivesInsertedRmvFromTotalLitresWhenNoRmvGiven() {
        // total 1800 L, avg depth 10 m -> 2.0 ata, 30 min -> 1800 / (2.0 * 30) = 30 L/min.
        final var result =
                GasConsumptionComparison.of(
                        new DiveGasConsumption(0, 0, 1800.0),
                        calculated(31.0, 1850.0),
                        10.0,
                        THIRTY_MIN);

        assertThat(result.impliedRmvFromTotalLiters()).isEqualTo(30.0);
        assertThat(result.insertedRmvLiters()).isEqualTo(30.0);
        assertThat(result.mismatch()).isFalse();
    }

    @Test
    void flagsAnInternalInsertedRmvVersusTotalInconsistency() {
        // rmv says 12, but total 1800 L over 2.0 ata * 30 min implies 30 -> internally
        // inconsistent.
        final var result =
                GasConsumptionComparison.of(
                        new DiveGasConsumption(0, 12.0, 1800.0),
                        CylinderConsumptionResult.EMPTY,
                        10.0,
                        THIRTY_MIN);

        assertThat(result.insertedRmvLiters()).isEqualTo(12.0);
        assertThat(result.impliedRmvFromTotalLiters()).isEqualTo(30.0);
        assertThat(result.mismatch()).isTrue();
    }
}
