package ch.sthomas.stddivegastracker.calc.allocation;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.DrawCharge;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.MemberAllocation;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.VesselAllocation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Cost allocation (DOMAIN §7): pure and deterministic, {@code BigDecimal}/{@code DECIMAL128}
 * throughout, no rounding. Every sum runs in id order, so results do not depend on the order of the
 * input lists (§7.5 #5).
 */
public final class AllocationEngine {

    private static final MathContext MC = MathContext.DECIMAL128;

    private AllocationEngine() {}

    public static AllocationResult allocate(final AllocationInput input) {
        Objects.requireNonNull(input, "input");

        final var vessels = new TreeMap<Long, Vessel>();
        for (final var vessel : input.vessels()) {
            vessels.put(vessel.vesselId(), vessel);
        }
        final var draws =
                input.draws().stream().sorted(Comparator.comparingLong(Draw::drawId)).toList();

        final var drawsFrom = new HashMap<Long, List<Draw>>();
        final var drawsInto = new HashMap<Long, List<Draw>>();
        for (final var draw : draws) {
            drawsFrom.computeIfAbsent(draw.sourceVesselId(), _ -> new ArrayList<>()).add(draw);
            if (draw.beneficiary() instanceof ToVessel(final var vesselId)) {
                drawsInto.computeIfAbsent(vesselId, _ -> new ArrayList<>()).add(draw);
            }
        }

        // DOMAIN §7.4: sources before targets, so every incoming charge is known when a vessel
        // runs.
        final var chargeByDraw = new HashMap<Long, BigDecimal>();
        final var provisionalVessels = new HashSet<Long>();
        final var allocations = new TreeMap<Long, VesselAllocation>();
        for (final long vesselId : topologicalOrder(vessels.keySet(), draws)) {
            allocations.put(
                    vesselId,
                    allocateVessel(
                            Objects.requireNonNull(vessels.get(vesselId)),
                            drawsFrom.getOrDefault(vesselId, List.of()),
                            drawsInto.getOrDefault(vesselId, List.of()),
                            input.activeMemberIds(),
                            chargeByDraw,
                            provisionalVessels));
        }

        final var drawCharges = new ArrayList<DrawCharge>();
        for (final var draw : draws) {
            drawCharges.add(
                    new DrawCharge(
                            draw.drawId(),
                            Objects.requireNonNull(chargeByDraw.get(draw.drawId()))));
        }
        return new AllocationResult(
                List.copyOf(allocations.values()),
                drawCharges,
                memberTotals(input, draws, chargeByDraw, allocations.values()));
    }

    /** Kahn's algorithm, lowest id first; a leftover vessel means a cycle (DOMAIN §7.4). */
    private static List<Long> topologicalOrder(final Set<Long> vesselIds, final List<Draw> draws) {
        final var successors = new HashMap<Long, Set<Long>>();
        final var inDegree = new HashMap<Long, Integer>();
        for (final long vesselId : vesselIds) {
            inDegree.put(vesselId, 0);
        }
        for (final var draw : draws) {
            if (draw.beneficiary() instanceof ToVessel(final var targetId)
                    && successors
                            .computeIfAbsent(draw.sourceVesselId(), _ -> new HashSet<>())
                            .add(targetId)) {
                inDegree.merge(targetId, 1, Integer::sum);
            }
        }

        final var ready = new TreeSet<Long>();
        inDegree.forEach(
                (vesselId, degree) -> {
                    if (degree == 0) {
                        ready.add(vesselId);
                    }
                });
        final var order = new ArrayList<Long>();
        while (!ready.isEmpty()) {
            final long vesselId = ready.first();
            ready.remove(vesselId);
            order.add(vesselId);
            for (final long next : successors.getOrDefault(vesselId, Set.of())) {
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        if (order.size() != vesselIds.size()) {
            final var stuck = new TreeSet<Long>(vesselIds);
            stuck.removeAll(order);
            throw new AllocationInvariantException(
                    "Vessel draws form a cycle involving vessels " + stuck);
        }
        return order;
    }

    private static VesselAllocation allocateVessel(
            final Vessel vessel,
            final List<Draw> drawsOut,
            final List<Draw> drawsIn,
            final Set<Long> activeMemberIds,
            final Map<Long, BigDecimal> chargeByDraw,
            final Set<Long> provisionalVessels) {
        // C = C_direct + upstream charges; D = Σ litres drawn (DOMAIN §7.2).
        var totalCost = BigDecimal.ZERO;
        for (final var line : sortedCostLines(vessel)) {
            totalCost = totalCost.add(line.amount(), MC);
        }
        for (final var draw : drawsIn) {
            totalCost = totalCost.add(chargeOf(chargeByDraw, draw), MC);
        }
        var drawn = BigDecimal.ZERO;
        for (final var draw : drawsOut) {
            drawn = drawn.add(BigDecimal.valueOf(draw.litres()), MC);
        }

        final boolean closed = vessel.status() == VesselStatus.CLOSED;
        // DOMAIN §7.1: the price can still change - open TRUE_UP, or fed by such a vessel.
        final boolean provisional =
                (vessel.policy() == LossPolicy.TRUE_UP && !closed)
                        || drawsIn.stream()
                                .anyMatch(d -> provisionalVessels.contains(d.sourceVesselId()));
        if (provisional) {
            provisionalVessels.add(vessel.vesselId());
        }
        final var rate = rate(vessel, closed, totalCost, drawn);
        var allocated = BigDecimal.ZERO;
        for (final var draw : drawsOut) {
            final var charge = rate.multiply(BigDecimal.valueOf(draw.litres()), MC);
            chargeByDraw.put(draw.drawId(), charge);
            allocated = allocated.add(charge, MC);
        }

        final var lossShares =
                closed
                        ? lossShares(vessel, totalCost, rate, drawn, activeMemberIds)
                        : List.<LossShare>of();
        for (final var share : lossShares) {
            allocated = allocated.add(share.amount(), MC);
        }
        return new VesselAllocation(
                vessel.vesselId(),
                rate,
                provisional,
                totalCost,
                drawn,
                allocated,
                totalCost.subtract(allocated, MC),
                lossShares);
    }

    /** Rates per DOMAIN §7.3; a non-positive denominator yields 0, never an error. */
    private static BigDecimal rate(
            final Vessel vessel,
            final boolean closed,
            final BigDecimal totalCost,
            final BigDecimal drawn) {
        final var inflow = BigDecimal.valueOf(vessel.inflowLitres());
        return switch (vessel.policy()) {
            case POOLED -> divideOrZero(totalCost, inflow);
            case TRUE_UP ->
                    closed
                            ? divideOrZero(totalCost, drawn)
                            : divideOrZero(
                                    totalCost,
                                    inflow.subtract(BigDecimal.valueOf(vessel.residualLitres()), MC)
                                            .max(drawn));
        };
    }

    /**
     * Loss (POOLED) or write-off (TRUE_UP, D = 0) of a closed vessel; otherwise nothing to share.
     */
    private static List<LossShare> lossShares(
            final Vessel vessel,
            final BigDecimal totalCost,
            final BigDecimal rate,
            final BigDecimal drawn,
            final Set<Long> activeMemberIds) {
        return switch (vessel.policy()) {
            case POOLED ->
                    split(
                            vessel,
                            totalCost.subtract(rate.multiply(drawn, MC), MC),
                            activeMemberIds);
            case TRUE_UP ->
                    drawn.signum() == 0 ? split(vessel, totalCost, activeMemberIds) : List.of();
        };
    }

    /**
     * Equal split among the closing participants, with the fallbacks of DOMAIN §7.3: snapshot, else
     * active members, else the cost-line payers pro-rata to what they paid.
     */
    private static List<LossShare> split(
            final Vessel vessel, final BigDecimal amount, final Set<Long> activeMemberIds) {
        final var equalShareholders =
                new TreeSet<Long>(
                        vessel.closingParticipantIds().isEmpty()
                                ? activeMemberIds
                                : vessel.closingParticipantIds());
        if (!equalShareholders.isEmpty()) {
            final var share = amount.divide(BigDecimal.valueOf(equalShareholders.size()), MC);
            return equalShareholders.stream().map(id -> new LossShare(id, share)).toList();
        }

        final var paidByPayer = new TreeMap<Long, BigDecimal>();
        for (final var line : sortedCostLines(vessel)) {
            paidByPayer.merge(line.payerMemberId(), line.amount(), (a, b) -> a.add(b, MC));
        }
        final var totalPaid = sum(paidByPayer.values());
        if (totalPaid.signum() <= 0) {
            throw new AllocationInvariantException(
                    "Vessel "
                            + vessel.vesselId()
                            + " is closed but has no loss participants: empty snapshot, no active"
                            + " member and no payer");
        }
        final var shares = new ArrayList<LossShare>();
        paidByPayer.forEach(
                (payerId, paid) -> {
                    if (paid.signum() > 0) {
                        shares.add(
                                new LossShare(
                                        payerId, amount.multiply(paid, MC).divide(totalPaid, MC)));
                    }
                });
        return shares;
    }

    /** Fill charges and loss shares per member; members named only as ids get zero totals. */
    private static List<MemberAllocation> memberTotals(
            final AllocationInput input,
            final List<Draw> draws,
            final Map<Long, BigDecimal> chargeByDraw,
            final Iterable<VesselAllocation> allocations) {
        final var memberIds = new TreeSet<Long>(input.activeMemberIds());
        for (final var vessel : input.vessels()) {
            memberIds.addAll(vessel.closingParticipantIds());
            vessel.costLines().forEach(line -> memberIds.add(line.payerMemberId()));
        }
        final var fillCharges = new HashMap<Long, BigDecimal>();
        for (final var draw : draws) {
            if (draw.beneficiary() instanceof ToMember(final var memberId)) {
                memberIds.add(memberId);
                fillCharges.merge(memberId, chargeOf(chargeByDraw, draw), (a, b) -> a.add(b, MC));
            }
        }
        final var lossShares = new HashMap<Long, BigDecimal>();
        for (final var allocation : allocations) {
            for (final var share : allocation.lossShares()) {
                memberIds.add(share.memberId());
                lossShares.merge(share.memberId(), share.amount(), (a, b) -> a.add(b, MC));
            }
        }

        final var members = new ArrayList<MemberAllocation>();
        for (final long memberId : memberIds) {
            members.add(
                    new MemberAllocation(
                            memberId,
                            fillCharges.getOrDefault(memberId, BigDecimal.ZERO),
                            lossShares.getOrDefault(memberId, BigDecimal.ZERO)));
        }
        return members;
    }

    private static BigDecimal chargeOf(final Map<Long, BigDecimal> chargeByDraw, final Draw draw) {
        return Objects.requireNonNull(chargeByDraw.get(draw.drawId()));
    }

    private static List<CostLine> sortedCostLines(final Vessel vessel) {
        return vessel.costLines().stream()
                .sorted(
                        Comparator.comparingLong(CostLine::payerMemberId)
                                .thenComparing(CostLine::amount))
                .toList();
    }

    private static BigDecimal divideOrZero(final BigDecimal numerator, final BigDecimal divisor) {
        return divisor.signum() <= 0 ? BigDecimal.ZERO : numerator.divide(divisor, MC);
    }

    private static BigDecimal sum(final Iterable<BigDecimal> values) {
        var total = BigDecimal.ZERO;
        for (final var value : values) {
            total = total.add(value, MC);
        }
        return total;
    }
}
