package ch.sthomas.stddivegastracker.calc.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationEngine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationScenarios;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationScenarios.Mode;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.MemberBalance;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.Payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Balance identity (DOMAIN §7.5 #3): payments cancel out, so {@code Σ_members balance = Σ_open
 * vessels unallocated}. Runs on C4's seeded random scenarios ({@link AllocationScenarios}) in all
 * three policy modes, with random payments on top. A failure reports mode, seed and scenario index;
 * scenario {@code i} is regenerated from {@code baseSeed + i} alone.
 */
final class BalanceIdentityContractTest {

    private static final int SCENARIOS = 1000;
    private static final int MIN_CHECKED = 500;
    private static final BigDecimal TOLERANCE = new BigDecimal("1e-9");

    /** Payments may name member MEMBERS + 2, who appears nowhere else. */
    private static final int PAYMENT_MEMBERS = AllocationScenarios.MEMBERS + 2;

    private static final class Coverage {
        int checked;
        int withInventoryValue;
        int withProvisional;
        int withPayments;
        int withPaymentOnlyMember;
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void balancesSumToTheUnallocatedValueOfOpenVessels(final Mode mode) {
        final var coverage = new Coverage();
        for (int i = 0; i < SCENARIOS; i++) {
            final long scenarioSeed = mode.baseSeed + i;
            final var input = AllocationScenarios.randomInput(new Random(scenarioSeed), mode);
            final var payments = randomPayments(new Random(~scenarioSeed));
            try {
                checkScenario(input, payments, new Random(scenarioSeed * 31), coverage);
            } catch (final AssertionError | RuntimeException e) {
                throw new AssertionError(
                        "Contract violated: mode="
                                + mode
                                + ", baseSeed="
                                + mode.baseSeed
                                + ", scenario="
                                + i
                                + " (scenarioSeed="
                                + scenarioSeed
                                + ")\n"
                                + e.getMessage()
                                + "\ninput: "
                                + input
                                + "\npayments: "
                                + payments,
                        e);
            }
        }

        assertThat(coverage.checked).as("checked scenarios").isGreaterThanOrEqualTo(MIN_CHECKED);
        assertThat(coverage.withInventoryValue).as("open inventory > 0").isGreaterThan(200);
        assertThat(coverage.withPayments).as("scenarios with payments").isGreaterThan(500);
        assertThat(coverage.withPaymentOnlyMember).as("payment-only member").isGreaterThan(50);
        // Provisional vessels (open TRUE_UP or fed by one) only exist where TRUE_UP does.
        assertThat(coverage.withProvisional > 0).isEqualTo(mode != Mode.ALL_POOLED);
        if (mode != Mode.ALL_POOLED) {
            assertThat(coverage.withProvisional).as("provisional charges").isGreaterThan(100);
        }
    }

    /** 0..5 payments among members 1..PAYMENT_MEMBERS, never from a member to themselves. */
    private static List<Payment> randomPayments(final Random rnd) {
        final var payments = new ArrayList<Payment>();
        for (int n = rnd.nextInt(6); n > 0; n--) {
            final long from = 1L + rnd.nextInt(PAYMENT_MEMBERS);
            long to = 1L + rnd.nextInt(PAYMENT_MEMBERS - 1);
            if (to >= from) {
                to++;
            }
            payments.add(new Payment(from, to, BigDecimal.valueOf(1 + rnd.nextInt(500_000), 2)));
        }
        return payments;
    }

    private static void checkScenario(
            final AllocationInput input,
            final List<Payment> payments,
            final Random shuffleRnd,
            final Coverage coverage) {
        final AllocationResult result;
        try {
            result = AllocationEngine.allocate(input);
        } catch (final IllegalStateException e) {
            // Closed vessel with a loss to split and nobody to split it among (DOMAIN §7.3).
            assertThat(e.getMessage()).contains("is closed");
            return;
        }
        coverage.checked++;

        final var rows = BalanceCalculator.calculate(input, result, payments);
        final var withoutPayments = BalanceCalculator.calculate(input, result, List.of());

        // Rows: every member named anywhere, once, sorted by id.
        final var named = new HashSet<Long>();
        result.members().forEach(m -> named.add(m.memberId()));
        input.vessels()
                .forEach(v -> v.costLines().forEach(line -> named.add(line.payerMemberId())));
        payments.forEach(
                p -> {
                    named.add(p.fromMemberId());
                    named.add(p.toMemberId());
                });
        assertThat(rows)
                .extracting(MemberBalance::memberId)
                .isSorted()
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(named);
        if (!payments.isEmpty()) {
            coverage.withPayments++;
        }
        if (rows.size() > withoutPayments.size()) {
            coverage.withPaymentOnlyMember++;
        }

        // §8: balance = paid - fill - loss + sent - received, per member.
        var balanceTotal = BigDecimal.ZERO;
        var sentTotal = BigDecimal.ZERO;
        var receivedTotal = BigDecimal.ZERO;
        for (final var row : rows) {
            assertClose(
                    row.balance(),
                    row.paidCosts()
                            .subtract(row.fillCharges())
                            .subtract(row.lossShares())
                            .add(row.paymentsSent())
                            .subtract(row.paymentsReceived()),
                    "balance of " + row.memberId());
            assertThat(row.provisionalCharges())
                    .as("provisional of %d", row.memberId())
                    .isNotNegative()
                    .isLessThanOrEqualTo(row.fillCharges().add(TOLERANCE));
            balanceTotal = balanceTotal.add(row.balance());
            sentTotal = sentTotal.add(row.paymentsSent());
            receivedTotal = receivedTotal.add(row.paymentsReceived());
        }

        // §7.5 #3: payments cancel out; what remains is the inventory value of open vessels.
        var openUnallocated = BigDecimal.ZERO;
        var allUnallocated = BigDecimal.ZERO;
        for (final var v : input.vessels()) {
            final var unallocated = result.vessel(v.vesselId()).unallocated();
            allUnallocated = allUnallocated.add(unallocated);
            if (v.status() == VesselStatus.IN_SERVICE) {
                openUnallocated = openUnallocated.add(unallocated);
            }
        }
        assertClose(balanceTotal, openUnallocated, "Σ balance = Σ open unallocated");
        assertClose(balanceTotal, allUnallocated, "Σ balance = Σ unallocated");
        assertClose(sentTotal, receivedTotal, "Σ sent = Σ received");
        assertClose(
                balanceTotal,
                withoutPayments.stream()
                        .map(MemberBalance::balance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                "payments leave Σ balance unchanged");
        if (openUnallocated.abs().compareTo(TOLERANCE) > 0) {
            coverage.withInventoryValue++;
        }

        // provisionalCharges: independent, transitive oracle over the input (open TRUE_UP, or fed
        // by such a vessel).
        final var provisionalSources = AllocationScenarios.provisionalVessels(input);
        boolean provisionalSeen = false;
        for (final var row : rows) {
            var expected = BigDecimal.ZERO;
            for (final var d : input.draws()) {
                if (d.beneficiary() instanceof ToMember(final var memberId)
                        && memberId == row.memberId()
                        && provisionalSources.contains(d.sourceVesselId())) {
                    expected = expected.add(result.drawCharge(d.drawId()).charge());
                }
            }
            assertClose(row.provisionalCharges(), expected, "provisional of " + row.memberId());
            provisionalSeen |= expected.signum() > 0;
        }
        if (provisionalSeen) {
            coverage.withProvisional++;
        }

        // Deterministic, and independent of the order of the payments.
        final var shuffled = new ArrayList<Payment>(payments);
        Collections.shuffle(shuffled, shuffleRnd);
        assertThat(BalanceCalculator.calculate(input, result, shuffled)).isEqualTo(rows);
    }

    private static void assertClose(
            final BigDecimal actual, final BigDecimal expected, final String what) {
        assertThat(actual.subtract(expected).abs())
                .as("%s: actual=%s expected=%s", what, actual, expected)
                .isLessThan(TOLERANCE);
    }
}
