package ch.sthomas.stddivegastracker.calc.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationEngine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.MemberBalance;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.Payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Specifics of {@link BalanceCalculator}; the golden numbers are in SettlementGoldenTest. */
final class BalanceCalculatorTest {

    private static final long A = 1;
    private static final long B = 2;
    private static final long C = 3;

    private static BigDecimal money(final String amount) {
        return new BigDecimal(amount);
    }

    private static Payment payment(final long from, final long to, final String amount) {
        return new Payment(from, to, money(amount));
    }

    /** DOMAIN §11.4 open: H costs 600.00 (A), rate 0.12 -> A 60.00, B 180.00, C 120.00. */
    private static AllocationInput openHelium() {
        return new AllocationInput(
                List.of(
                        new Vessel(
                                10,
                                LossPolicy.TRUE_UP,
                                VesselStatus.IN_SERVICE,
                                List.of(new CostLine(A, money("600.00"))),
                                6000,
                                1000,
                                List.of())),
                List.of(
                        new Draw(1, 10, 1500, new ToMember(B)),
                        new Draw(2, 10, 1000, new ToMember(C)),
                        new Draw(3, 10, 500, new ToMember(A))),
                Set.of(A, B, C));
    }

    private static List<MemberBalance> calculate(
            final AllocationInput input, final List<Payment> payments) {
        return BalanceCalculator.calculate(input, AllocationEngine.allocate(input), payments);
    }

    private static BigDecimal total(final List<MemberBalance> rows) {
        return rows.stream().map(MemberBalance::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void paymentMovesBothSidesAndKeepsTheTotal() {
        final var before = calculate(openHelium(), List.of());
        final var after = calculate(openHelium(), List.of(payment(B, A, "100.00")));

        // B pays A 100.00: B -180 -> -80, A +540 -> +440, C untouched.
        assertThat(after.get(0).balance()).isEqualByComparingTo("440.00");
        assertThat(after.get(0).paymentsReceived()).isEqualByComparingTo("100.00");
        assertThat(after.get(0).paymentsSent()).isEqualByComparingTo("0");
        assertThat(after.get(1).balance()).isEqualByComparingTo("-80.00");
        assertThat(after.get(1).paymentsSent()).isEqualByComparingTo("100.00");
        assertThat(after.get(1).paymentsReceived()).isEqualByComparingTo("0");
        assertThat(after.get(2)).isEqualTo(before.get(2));

        assertThat(total(before)).isEqualByComparingTo("240.00");
        assertThat(total(after)).isEqualByComparingTo("240.00");
    }

    @Test
    void paymentsAreSummedPerSideAndIndependentOfTheirOrder() {
        final var payments =
                List.of(
                        payment(B, A, "10.00"),
                        payment(A, B, "2.50"),
                        payment(B, C, "7.25"),
                        payment(B, A, "10.00"));
        final var rows = calculate(openHelium(), payments);

        // B sent 10.00 + 7.25 + 10.00, received 2.50; A sent 2.50, received 20.00.
        assertThat(rows.get(1).paymentsSent()).isEqualByComparingTo("27.25");
        assertThat(rows.get(1).paymentsReceived()).isEqualByComparingTo("2.50");
        assertThat(rows.get(0).paymentsSent()).isEqualByComparingTo("2.50");
        assertThat(rows.get(0).paymentsReceived()).isEqualByComparingTo("20.00");
        assertThat(rows.get(0).balance()).isEqualByComparingTo("522.50"); // 540 + 2.50 - 20.00
        assertThat(rows.get(1).balance()).isEqualByComparingTo("-155.25"); // -180 + 27.25 - 2.50
        assertThat(rows.get(2).balance()).isEqualByComparingTo("-127.25"); // -120 - 7.25
        assertThat(total(rows)).isEqualByComparingTo("240.00");

        assertThat(calculate(openHelium(), payments.reversed())).isEqualTo(rows);
    }

    @Test
    void memberOnlyNamedInAPaymentIsListedInIdOrder() {
        final var rows =
                calculate(openHelium(), List.of(payment(9, A, "50.00"), payment(B, 7, "5.00")));

        assertThat(rows).extracting(MemberBalance::memberId).containsExactly(A, B, C, 7L, 9L);
        assertThat(rows.get(3).balance()).isEqualByComparingTo("-5.00"); // received 5.00
        assertThat(rows.get(4).balance()).isEqualByComparingTo("50.00"); // sent 50.00
        assertThat(rows.get(3).paidCosts()).isEqualByComparingTo("0");
        assertThat(total(rows)).isEqualByComparingTo("240.00");
    }

    /** Payers who are not active members (left the team) still get a row. */
    @Test
    void costLinePayerOutsideTheActiveMembersIsListed() {
        final var input =
                new AllocationInput(
                        List.of(
                                new Vessel(
                                        10,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.IN_SERVICE,
                                        List.of(new CostLine(5, money("40.00"))),
                                        100,
                                        0,
                                        List.of())),
                        List.of(new Draw(1, 10, 50, new ToMember(A))),
                        Set.of(A));
        final var rows = calculate(input, List.of());

        assertThat(rows).extracting(MemberBalance::memberId).containsExactly(A, 5L);
        assertThat(rows.get(1).paidCosts()).isEqualByComparingTo("40.00");
        assertThat(rows.get(1).balance()).isEqualByComparingTo("40.00");
        assertThat(rows.get(0).balance()).isEqualByComparingTo("-20.00");
    }

    /**
     * Costs 100.00 per vessel (A pays 300.00 in all); B draws from every vessel, C from the house
     * mix X that V10 feeds. Only charges of draws from provisional vessels count: open TRUE_UP, or
     * (transitively) fed by one - the open POOLED mix X is provisional because V10 feeds it.
     */
    @Test
    void provisionalChargesCountOnlyDrawsFromProvisionalVessels() {
        final long trueUpOpen = 10;
        final long pooledOpen = 11;
        final long trueUpClosed = 12;
        final long houseMix = 14;
        final var input =
                new AllocationInput(
                        List.of(
                                vessel(
                                        trueUpOpen,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.IN_SERVICE,
                                        1000),
                                vessel(
                                        pooledOpen,
                                        LossPolicy.POOLED,
                                        VesselStatus.IN_SERVICE,
                                        1000),
                                vessel(trueUpClosed, LossPolicy.TRUE_UP, VesselStatus.CLOSED, 1000),
                                new Vessel(
                                        houseMix,
                                        LossPolicy.POOLED,
                                        VesselStatus.IN_SERVICE,
                                        List.of(),
                                        200,
                                        0,
                                        List.of())),
                        List.of(
                                new Draw(1, trueUpOpen, 100, new ToMember(B)), // 0.1 -> 10.00
                                new Draw(
                                        2,
                                        trueUpOpen,
                                        100,
                                        new ToVessel(houseMix)), // not a member charge
                                new Draw(
                                        3,
                                        pooledOpen,
                                        100,
                                        new ToMember(B)), // POOLED 0.1 -> 10.00, not provisional
                                new Draw(
                                        4,
                                        trueUpClosed,
                                        50,
                                        new ToMember(B)), // closed TRUE_UP 2.0 -> 100.00, final
                                new Draw(
                                        5,
                                        houseMix,
                                        10,
                                        new ToMember(C))), // 10.00 / 200 -> 0.50, inherited
                        Set.of(A, B, C));
        final var rows = calculate(input, List.of());

        assertThat(rows.get(0).paidCosts()).isEqualByComparingTo("300.00");
        assertThat(rows.get(0).fillCharges()).isEqualByComparingTo("0");
        assertThat(rows.get(0).provisionalCharges()).isEqualByComparingTo("0");
        assertThat(rows.get(1).fillCharges()).isEqualByComparingTo("120.00");
        assertThat(rows.get(1).provisionalCharges()).isEqualByComparingTo("10.00");
        assertThat(rows.get(2).fillCharges()).isEqualByComparingTo("0.50");
        assertThat(rows.get(2).provisionalCharges()).isEqualByComparingTo("0.50");
        // Σ balances = unallocated of the open vessels: 80.00 + 90.00 + 9.50.
        assertThat(total(rows)).isEqualByComparingTo("179.50");
    }

    private static Vessel vessel(
            final long vesselId,
            final LossPolicy policy,
            final VesselStatus status,
            final double inflowLitres) {
        return new Vessel(
                vesselId,
                policy,
                status,
                List.of(new CostLine(A, money("100.00"))),
                inflowLitres,
                0,
                List.of(A));
    }

    @Test
    void noFactsNoRows() {
        final var input = new AllocationInput(List.of(), List.of(), Set.of());
        final var result = AllocationEngine.allocate(input);

        assertThat(BalanceCalculator.calculate(input, result, List.of())).isEmpty();
    }

    @Test
    void paymentIsValidated() {
        assertThatThrownBy(() -> payment(A, B, "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than 0");
        assertThatThrownBy(() -> payment(A, B, "-1.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment(A, A, "1.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two different members");
        assertThat(payment(A, B, "0.01").amount()).isEqualByComparingTo("0.01");
    }
}
