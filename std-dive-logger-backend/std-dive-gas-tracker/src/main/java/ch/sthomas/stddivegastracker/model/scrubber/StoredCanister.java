package ch.sthomas.stddivegastracker.model.scrubber;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A canister as stored, no money derived yet: the service prices it. */
public record StoredCanister(
        long canisterId,
        String label,
        double capacityKg,
        Instant openedAt,
        @Nullable Instant emptiedAt,
        long createdByMemberId,
        List<CostLine> costLines,
        List<Use> uses) {

    public StoredCanister {
        costLines = List.copyOf(costLines);
        uses = List.copyOf(uses);
    }

    public record CostLine(
            long costLineId,
            long payerMemberId,
            BigDecimal amount,
            Instant paidAt,
            @Nullable String note) {}

    public record Use(
            long useId, long memberId, double kg, Instant usedAt, long recordedByMemberId) {}
}
