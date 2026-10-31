package ch.sthomas.stddivegastracker.model.scrubber;

import ch.sthomas.stddivegastracker.model.team.MemberRef;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API.md §7 {@code ScrubberCanister}: a canister with its costs and uses, newest use first. {@code
 * remainingKg} is an estimate from what was recorded, 0 once emptied; {@code isEstimate}: its price
 * can still change.
 */
public record ScrubberCanister(
        long canisterId,
        String label,
        CanisterStatus status,
        double capacityKg,
        double recordedKg,
        double remainingKg,
        BigDecimal totalCost,
        BigDecimal pricePerKg,
        boolean isEstimate,
        Instant openedAt,
        @Nullable Instant emptiedAt,
        MemberRef createdBy,
        List<CostLine> costLines,
        List<Use> uses) {

    public ScrubberCanister {
        costLines = List.copyOf(costLines);
        uses = List.copyOf(uses);
    }

    @Schema(name = "ScrubberCostLine")
    public record CostLine(
            long costLineId,
            MemberRef payer,
            BigDecimal amount,
            Instant paidAt,
            @Nullable String note) {}

    @Schema(name = "ScrubberUse")
    public record Use(
            long useId,
            MemberRef member,
            double kg,
            BigDecimal charge,
            Instant usedAt,
            MemberRef recordedBy) {}
}
