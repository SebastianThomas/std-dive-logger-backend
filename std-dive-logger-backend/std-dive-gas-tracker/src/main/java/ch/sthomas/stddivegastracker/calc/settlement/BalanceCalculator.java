package ch.sthomas.stddivegastracker.calc.settlement;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.MemberAllocation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Member balances (DOMAIN §8), unrounded {@code BigDecimal}/{@code DECIMAL128}. Like the engine,
 * every sum runs in id order (payments: by from, to, amount), so results do not depend on input
 * order.
 */
public final class BalanceCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private BalanceCalculator() {}

    /** A payment between two team members (DOMAIN §8): {@code amount > 0}, {@code from != to}. */
    public record Payment(long fromMemberId, long toMemberId, BigDecimal amount) {

        public Payment {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Payment amount must be greater than 0");
            }
            if (fromMemberId == toMemberId) {
                throw new IllegalArgumentException("A payment needs two different members");
            }
        }
    }

    /**
     * One member's balance and its components (DOMAIN §8). {@code provisionalCharges} is the part
     * of {@code fillCharges} drawn from vessels whose rate can still change (open TRUE_UP, or fed
     * by such a vessel; {@code VesselAllocation.provisional}).
     */
    public record MemberBalance(
            long memberId,
            BigDecimal paidCosts,
            BigDecimal fillCharges,
            BigDecimal lossShares,
            BigDecimal paymentsSent,
            BigDecimal paymentsReceived,
            BigDecimal balance,
            BigDecimal provisionalCharges) {}

    /** One row per member named anywhere (input, result or payments), sorted by member id. */
    public static List<MemberBalance> calculate(
            final AllocationInput input,
            final AllocationResult result,
            final List<Payment> payments) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(result, "result");

        final var paid = paidCosts(input);
        final var sent = new TreeMap<Long, BigDecimal>();
        final var received = new TreeMap<Long, BigDecimal>();
        payments.stream()
                .sorted(
                        Comparator.comparingLong(Payment::fromMemberId)
                                .thenComparingLong(Payment::toMemberId)
                                .thenComparing(Payment::amount))
                .forEach(
                        payment -> {
                            sent.merge(
                                    payment.fromMemberId(),
                                    payment.amount(),
                                    BalanceCalculator::add);
                            received.merge(
                                    payment.toMemberId(), payment.amount(), BalanceCalculator::add);
                        });
        final var provisional = provisionalCharges(input, result);

        final var memberIds = new TreeSet<Long>(paid.keySet());
        memberIds.addAll(sent.keySet());
        memberIds.addAll(received.keySet());
        result.members().forEach(member -> memberIds.add(member.memberId()));

        final var allocations = new TreeMap<Long, MemberAllocation>();
        result.members().forEach(member -> allocations.put(member.memberId(), member));

        final var balances = new ArrayList<MemberBalance>();
        for (final long memberId : memberIds) {
            final var allocation =
                    allocations.getOrDefault(
                            memberId,
                            new MemberAllocation(memberId, BigDecimal.ZERO, BigDecimal.ZERO));
            final var paidCosts = paid.getOrDefault(memberId, BigDecimal.ZERO);
            final var fillCharges = allocation.fillCharges();
            final var lossShares = allocation.lossShares();
            final var paymentsSent = sent.getOrDefault(memberId, BigDecimal.ZERO);
            final var paymentsReceived = received.getOrDefault(memberId, BigDecimal.ZERO);
            balances.add(
                    new MemberBalance(
                            memberId,
                            paidCosts,
                            fillCharges,
                            lossShares,
                            paymentsSent,
                            paymentsReceived,
                            paidCosts
                                    .subtract(fillCharges, MC)
                                    .subtract(lossShares, MC)
                                    .add(paymentsSent, MC)
                                    .subtract(paymentsReceived, MC),
                            provisional.getOrDefault(memberId, BigDecimal.ZERO)));
        }
        return balances;
    }

    private static BigDecimal add(final BigDecimal a, final BigDecimal b) {
        return a.add(b, MC);
    }

    /** Σ cost lines per payer, vessels in id order, lines by (payer, amount). */
    private static Map<Long, BigDecimal> paidCosts(final AllocationInput input) {
        final var paid = new TreeMap<Long, BigDecimal>();
        input.vessels().stream()
                .sorted(Comparator.comparingLong(Vessel::vesselId))
                .flatMap(vessel -> vessel.costLines().stream())
                .sorted(
                        Comparator.comparingLong(CostLine::payerMemberId)
                                .thenComparing(CostLine::amount))
                .forEach(
                        line ->
                                paid.merge(
                                        line.payerMemberId(),
                                        line.amount(),
                                        BalanceCalculator::add));
        return paid;
    }

    /** Fill charges of draws from provisional vessels into a member's cylinder, draws by id. */
    private static Map<Long, BigDecimal> provisionalCharges(
            final AllocationInput input, final AllocationResult result) {
        final var provisional = new TreeMap<Long, BigDecimal>();
        input.draws().stream()
                .sorted(Comparator.comparingLong(Draw::drawId))
                .forEach(
                        draw -> {
                            if (draw.beneficiary() instanceof ToMember(final var memberId)
                                    && result.vessel(draw.sourceVesselId()).provisional()) {
                                provisional.merge(
                                        memberId,
                                        result.drawCharge(draw.drawId()).charge(),
                                        BalanceCalculator::add);
                            }
                        });
        return provisional;
    }
}
