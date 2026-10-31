package ch.sthomas.stddivegastracker.calc.scrubber;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a canister's absorbent costs (DOMAIN §12), unrounded. Nobody weighs a scoop, so an open
 * canister prices at cost / capacity (an estimate), or at cost / recorded kg once more than its
 * capacity is recorded; an emptied one at cost / recorded kg, so its charges add up to its cost.
 */
public final class ScrubberPricing {

    private static final MathContext MC = MathContext.DECIMAL128;

    private ScrubberPricing() {}

    public record Use(long useId, long memberId, double kg) {}

    public record CostLine(long payerMemberId, BigDecimal amount) {}

    public record Canister(
            long canisterId,
            double capacityKg,
            boolean emptied,
            List<CostLine> costLines,
            List<Use> uses) {

        public Canister {
            costLines = List.copyOf(costLines);
            uses = List.copyOf(uses);
        }
    }

    /** {@code unallocated}: paid for but not charged to anyone yet (absorbent still in stock). */
    public record Priced(
            BigDecimal totalCost,
            double recordedKg,
            BigDecimal pricePerKg,
            boolean isEstimate,
            BigDecimal unallocated,
            Map<Long, BigDecimal> chargeByUseId) {

        public Priced {
            chargeByUseId = Map.copyOf(chargeByUseId);
        }
    }

    /** One member's scrubber money across all canisters; part of the balance (DOMAIN §8). */
    public record MemberTotal(
            long memberId, BigDecimal paid, BigDecimal charged, BigDecimal provisionalCharged) {}

    public static Priced price(final Canister canister) {
        final var totalCost =
                canister.costLines().stream()
                        .map(CostLine::amount)
                        .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        final double recordedKg = canister.uses().stream().mapToDouble(Use::kg).sum();
        final double dividingKg =
                canister.emptied() ? recordedKg : Math.max(canister.capacityKg(), recordedKg);
        final var pricePerKg =
                dividingKg > 0
                        ? totalCost.divide(BigDecimal.valueOf(dividingKg), MC)
                        : BigDecimal.ZERO;
        final var charges = new HashMap<Long, BigDecimal>();
        var charged = BigDecimal.ZERO;
        for (final var use : canister.uses()) {
            final var charge =
                    totalCost
                            .multiply(BigDecimal.valueOf(use.kg()), MC)
                            .divide(BigDecimal.valueOf(dividingKg), MC);
            charges.put(use.useId(), charge);
            charged = charged.add(charge, MC);
        }
        return new Priced(
                totalCost,
                recordedKg,
                pricePerKg,
                !canister.emptied(),
                totalCost.subtract(charged, MC),
                charges);
    }

    /** One row per member named on a cost line or a use, sorted by member id. */
    public static List<MemberTotal> memberTotals(final List<Canister> canisters) {
        final var paid = new TreeMap<Long, BigDecimal>();
        final var charged = new TreeMap<Long, BigDecimal>();
        final var provisional = new TreeMap<Long, BigDecimal>();
        for (final var canister : canisters) {
            final var priced = price(canister);
            canister.costLines()
                    .forEach(
                            line ->
                                    paid.merge(
                                            line.payerMemberId(),
                                            line.amount(),
                                            ScrubberPricing::add));
            for (final var use : canister.uses()) {
                final var charge = priced.chargeByUseId().get(use.useId());
                charged.merge(use.memberId(), charge, ScrubberPricing::add);
                if (priced.isEstimate()) {
                    provisional.merge(use.memberId(), charge, ScrubberPricing::add);
                }
            }
        }
        final var memberIds = new TreeMap<Long, Boolean>();
        paid.keySet().forEach(id -> memberIds.put(id, true));
        charged.keySet().forEach(id -> memberIds.put(id, true));
        final var totals = new ArrayList<MemberTotal>();
        for (final long memberId : memberIds.keySet()) {
            totals.add(
                    new MemberTotal(
                            memberId,
                            paid.getOrDefault(memberId, BigDecimal.ZERO),
                            charged.getOrDefault(memberId, BigDecimal.ZERO),
                            provisional.getOrDefault(memberId, BigDecimal.ZERO)));
        }
        return totals;
    }

    private static BigDecimal add(final BigDecimal a, final BigDecimal b) {
        return a.add(b, MC);
    }
}
