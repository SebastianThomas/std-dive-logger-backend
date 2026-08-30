package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Reconciles the two independent sources of a dive's gas consumption: the <b>inserted</b>
 * whole-dive {@link DiveGasConsumption} a user/importer typed in, and the <b>calculated</b> OC
 * figures {@link CylinderConsumptionResult} derives from tracked cylinder pressures + the depth
 * profile. The calculated figures are the source of truth; the inserted ones are a fallback. A
 * disagreement means one side is wrong - surfaced as the {@code GAS_CONSUMPTION_MISMATCH} backfill
 * chip and an inline "show the working" warning on the dive view.
 *
 * <p>Three independent checks (all at {@link #MISMATCH_TOLERANCE} of the larger value):
 *
 * <ul>
 *   <li>{@code rmvVsCalculatedMismatch} - inserted RMV vs the RMV computed from tracked cylinders.
 *       Only possible when OC cylinders are tracked.
 *   <li>{@code totalLitersMismatch} - inserted total litres vs the litres the tracked cylinders
 *       actually gave up. The most direct pointer at a mistyped cylinder pressure/size.
 *   <li>{@code rmvVsImpliedMismatch} - inserted RMV vs the RMV its own inserted total litres +
 *       depth + duration imply. An internal-consistency check, the only one available on a dive
 *       with no tracked cylinders.
 * </ul>
 *
 * Headline precedence for the UI: RMV-vs-calculated, then total-litres, then RMV-vs-implied.
 */
public record GasConsumptionComparison(
        @Nullable Double insertedRmvLiters,
        @Nullable Double insertedTotalLiters,
        @Nullable Double impliedRmvFromTotalLiters,
        @Nullable Double calculatedRmvLiters,
        @Nullable Double calculatedTotalLiters,
        // Depth-weighted pressure-minutes behind calculatedRmvLiters, for the breakdown display.
        @Nullable Double ocPressureMinutes,
        @Nullable Double avgDepthMeters,
        @Nullable Double durationMinutes,
        boolean rmvVsCalculatedMismatch,
        boolean totalLitersMismatch,
        boolean rmvVsImpliedMismatch,
        boolean mismatch,
        // Per-cylinder Delta-bar -> litres lines so the diver can spot a mistyped pressure/size.
        List<CylinderContribution> contributions) {

    /** Fractional tolerance (of the larger value) before two figures count as disagreeing. */
    public static final double MISMATCH_TOLERANCE = 0.15;

    public static GasConsumptionComparison of(
            final DiveGasConsumption gasConsumption,
            final CylinderConsumptionResult cylinderConsumption,
            final @Nullable Double avgDepthMeters,
            final long durationSeconds) {
        final Double insertedTotal =
                gasConsumption.totalLiters() > 0 ? gasConsumption.totalLiters() : null;
        final var durationMinutes = durationSeconds / 60.0;
        final Double impliedRmv =
                (insertedTotal != null && avgDepthMeters != null && durationMinutes > 0)
                        ? insertedTotal / ((1 + avgDepthMeters / 10.0) * durationMinutes)
                        : null;
        // Keep this a *reference* conditional: a bare `rmvLiters() : impliedRmv` mixes primitive
        // double with a nullable Double, which JLS 15.25 makes a numeric conditional - impliedRmv
        // then gets unboxed even on the rmvLiters()>0 branch, NPEing when it's null (both manual
        // inputs cleared -> no total -> no implied RMV).
        final Double insertedRmv =
                gasConsumption.rmvLiters() > 0
                        ? Double.valueOf(gasConsumption.rmvLiters())
                        : impliedRmv;
        final var calculatedRmv = cylinderConsumption.ocRmvLiters();
        final var calculatedTotal = cylinderConsumption.ocConsumedLiters();

        final var rmvVsCalculatedMismatch = differsBeyondTolerance(insertedRmv, calculatedRmv);
        final var totalLitersMismatch = differsBeyondTolerance(insertedTotal, calculatedTotal);
        final var rmvVsImpliedMismatch =
                gasConsumption.rmvLiters() > 0
                        && differsBeyondTolerance(gasConsumption.rmvLiters(), impliedRmv);

        return new GasConsumptionComparison(
                insertedRmv,
                insertedTotal,
                impliedRmv,
                calculatedRmv,
                calculatedTotal,
                cylinderConsumption.ocPressureMinutes(),
                avgDepthMeters,
                durationMinutes > 0 ? durationMinutes : null,
                rmvVsCalculatedMismatch,
                totalLitersMismatch,
                rmvVsImpliedMismatch,
                rmvVsCalculatedMismatch || totalLitersMismatch || rmvVsImpliedMismatch,
                cylinderConsumption.contributions());
    }

    private static boolean differsBeyondTolerance(
            final @Nullable Double a, final @Nullable Double b) {
        if (a == null || b == null || a <= 0 || b <= 0) {
            return false;
        }
        return Math.abs(a - b) > MISMATCH_TOLERANCE * Math.max(a, b);
    }
}
