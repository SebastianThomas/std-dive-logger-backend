package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;

import org.jspecify.annotations.Nullable;

/**
 * Reconciles the two independent sources of a dive's gas consumption: the <b>inserted</b>
 * whole-dive {@link DiveGasConsumption} a user/importer typed in, and the <b>calculated</b> OC
 * figures {@link CylinderConsumptionResult} derives from tracked cylinder pressures + the depth
 * profile. A large disagreement means one side is wrong - surfaced as the {@code
 * GAS_CONSUMPTION_MISMATCH} backfill chip and an inline warning on the dive view.
 *
 * <ul>
 *   <li>{@code insertedRmvLiters} - {@code gasConsumption.rmvLiters} when {@code > 0}, else the
 *       total-litres-derived {@code impliedRmvFromTotalLiters}, else {@code null}.
 *   <li>{@code impliedRmvFromTotalLiters} - {@code totalLiters / ((1 + avgDepth/10) ·
 *       durationMinutes)} whenever computable, kept separate so the UI can flag an inserted
 *       RMV-vs-total internal inconsistency.
 *   <li>{@code calculatedRmvLiters} / {@code calculatedTotalLiters} - {@link
 *       CylinderConsumptionResult#ocRmvLiters()} / {@link
 *       CylinderConsumptionResult#ocConsumedLiters()}.
 *   <li>{@code mismatch} - true when a both-present pair differs by more than {@link
 *       #RMV_MISMATCH_TOLERANCE} of the larger: inserted vs calculated RMV, or inserted {@code
 *       rmvLiters} vs the implied-from-total value.
 * </ul>
 */
public record GasConsumptionComparison(
        @Nullable Double insertedRmvLiters,
        @Nullable Double insertedTotalLiters,
        @Nullable Double impliedRmvFromTotalLiters,
        @Nullable Double calculatedRmvLiters,
        @Nullable Double calculatedTotalLiters,
        boolean mismatch) {

    /** Fractional tolerance (of the larger value) before two RMV figures count as disagreeing. */
    public static final double RMV_MISMATCH_TOLERANCE = 0.15;

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
        final Double insertedRmv =
                gasConsumption.rmvLiters() > 0 ? gasConsumption.rmvLiters() : impliedRmv;
        final var calculatedRmv = cylinderConsumption.ocRmvLiters();
        final var calculatedTotal = cylinderConsumption.ocConsumedLiters();

        final var mismatch =
                differsBeyondTolerance(insertedRmv, calculatedRmv)
                        || (gasConsumption.rmvLiters() > 0
                                && differsBeyondTolerance(gasConsumption.rmvLiters(), impliedRmv));

        return new GasConsumptionComparison(
                insertedRmv, insertedTotal, impliedRmv, calculatedRmv, calculatedTotal, mismatch);
    }

    private static boolean differsBeyondTolerance(
            final @Nullable Double a, final @Nullable Double b) {
        if (a == null || b == null || a <= 0 || b <= 0) {
            return false;
        }
        return Math.abs(a - b) > RMV_MISMATCH_TOLERANCE * Math.max(a, b);
    }
}
