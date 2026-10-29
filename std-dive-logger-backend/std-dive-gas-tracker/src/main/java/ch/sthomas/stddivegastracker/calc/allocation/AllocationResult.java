package ch.sthomas.stddivegastracker.calc.allocation;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Engine output (DOMAIN §7.1), unrounded. Lists are sorted by id; {@code members} covers every
 * member named anywhere in the input (zero totals included).
 */
public record AllocationResult(
        List<VesselAllocation> vessels,
        List<DrawCharge> drawCharges,
        List<MemberAllocation> members) {

    public AllocationResult {
        vessels = sortedBy(vessels, VesselAllocation::vesselId);
        drawCharges = sortedBy(drawCharges, DrawCharge::drawId);
        members = sortedBy(members, MemberAllocation::memberId);
    }

    public VesselAllocation vessel(final long vesselId) {
        return byId(vessels, VesselAllocation::vesselId, vesselId, "vessel");
    }

    /** Looked up once per draw by balances and reports: a binary search, not a scan. */
    public DrawCharge drawCharge(final long drawId) {
        return byId(drawCharges, DrawCharge::drawId, drawId, "draw");
    }

    public MemberAllocation member(final long memberId) {
        return byId(members, MemberAllocation::memberId, memberId, "member");
    }

    private static <T> List<T> sortedBy(final List<T> items, final ToLongFunction<T> id) {
        return items.stream().sorted(Comparator.comparingLong(id)).toList();
    }

    private static <T> T byId(
            final List<T> sorted,
            final ToLongFunction<T> id,
            final long wanted,
            final String what) {
        var low = 0;
        var high = sorted.size() - 1;
        while (low <= high) {
            final var middle = (low + high) >>> 1;
            final var middleId = id.applyAsLong(sorted.get(middle));
            if (middleId < wanted) {
                low = middle + 1;
            } else if (middleId > wanted) {
                high = middle - 1;
            } else {
                return sorted.get(middle);
            }
        }
        throw new AllocationInvariantException("No " + what + " " + wanted);
    }

    /**
     * Per-vessel outcome (DOMAIN §7.2), all unrounded. {@code totalCost} is C incl. upstream,
     * {@code drawnLitres} is D, {@code lossShares} sorted by member id (empty while open). {@code
     * provisional}: the rate can still change (open TRUE_UP, or fed by a provisional vessel).
     */
    public record VesselAllocation(
            long vesselId,
            BigDecimal rate,
            boolean provisional,
            BigDecimal totalCost,
            BigDecimal drawnLitres,
            BigDecimal allocated,
            BigDecimal unallocated,
            List<LossShare> lossShares) {

        public VesselAllocation {
            lossShares = List.copyOf(lossShares);
        }
    }

    /** {@code rate(source) * litres} of one draw (DOMAIN §7.2). */
    public record DrawCharge(long drawId, BigDecimal charge) {}

    /** A member's share of a closed vessel's loss or write-off; negative = credit (§7.3). */
    public record LossShare(long memberId, BigDecimal amount) {}

    /** Member totals: Σ fill charges and Σ loss shares (DOMAIN §7.1). */
    public record MemberAllocation(long memberId, BigDecimal fillCharges, BigDecimal lossShares) {}
}
