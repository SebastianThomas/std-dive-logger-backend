package ch.sthomas.stddivegastracker.model.vessel;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.gas.GasClass;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.team.MemberRef;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API.md §3 {@code VesselDetail} = {@link VesselSummary} fields + the rest (flat, as the JSON is).
 * Money is rounded HALF_EVEN to 2 decimals; {@code lossReport} only for a closed vessel.
 */
public record VesselDetail(
        long vesselId,
        String label,
        GasClass gasClass,
        GasMix mix,
        double waterVolumeLitres,
        VesselStatus status,
        LossPolicy lossPolicy,
        double estimatedPressureBar,
        double estimatedContentLitres,
        double usableContentLitres,
        BigDecimal rate,
        boolean rateIsEstimate,
        Instant openedAt,
        @Nullable Instant closedAt,
        double initialPressureBar,
        double expectedResidualPressureBar,
        @Nullable Double closingPressureBar,
        @Nullable String notes,
        MemberRef createdBy,
        BigDecimal totalCost,
        BigDecimal allocated,
        BigDecimal unallocated,
        double inflowLitres,
        double drawnLitres,
        double bookContentLitres,
        List<CostLine> costLines,
        List<Reading> readings,
        @Nullable LossReport lossReport) {

    public VesselDetail {
        costLines = List.copyOf(costLines);
        readings = List.copyOf(readings);
    }

    public record CostLine(
            long costLineId,
            long payerMemberId,
            BigDecimal amount,
            Instant paidAt,
            @Nullable String note,
            MemberRef payer) {}

    /** {@code discrepancyLitres = book - measured} at the reading (DOMAIN §6). */
    public record Reading(
            long readingId,
            double pressureBar,
            Instant measuredAt,
            MemberRef createdBy,
            double bookPressureBar,
            double discrepancyLitres) {}

    /**
     * DOMAIN §6 loss report. {@code lossAmount}: POOLED, Σ loss shares; {@code writeOffAmount}:
     * TRUE_UP without draws, Σ shares (DOMAIN §7.3).
     */
    public record LossReport(
            double inflowLitres,
            double drawnLitres,
            double lineLossLitres,
            double residualLitres,
            double unaccountedLitres,
            double lossFraction,
            @Nullable BigDecimal lossAmount,
            @Nullable BigDecimal writeOffAmount) {}
}
