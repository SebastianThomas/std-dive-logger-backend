package ch.sthomas.stddivegastracker.calc.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationEngine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.MemberBalance;
import ch.sthomas.stddivegastracker.calc.settlement.SettlementSuggester.Transfer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Golden values of DOMAIN §11.4 and §11.5: balances (§8) and settlement suggestions. */
final class SettlementGoldenTest {

    private static final long A = 1;
    private static final long B = 2;
    private static final long C = 3;
    private static final long D = 4;
    private static final long HELIUM = 10;

    /**
     * §11.4 / §11.5 facts: H costs 600.00 (paid by A), V_in 6000, V_res 1000; B 1500, C 1000, A
     * 500.
     */
    private static AllocationInput heliumFacts(
            final LossPolicy policy,
            final VesselStatus status,
            final List<Long> closingParticipants,
            final Set<Long> activeMembers) {
        return new AllocationInput(
                List.of(
                        new Vessel(
                                HELIUM,
                                policy,
                                status,
                                List.of(new CostLine(A, new BigDecimal("600.00"))),
                                6000,
                                1000,
                                closingParticipants)),
                List.of(
                        new Draw(1, HELIUM, 1500, new ToMember(B)),
                        new Draw(2, HELIUM, 1000, new ToMember(C)),
                        new Draw(3, HELIUM, 500, new ToMember(A))),
                activeMembers);
    }

    private static List<MemberBalance> balances(final AllocationInput input) {
        return BalanceCalculator.calculate(input, AllocationEngine.allocate(input), List.of());
    }

    private static Transfer transfer(final long from, final long to, final String amount) {
        return new Transfer(from, to, new BigDecimal(amount));
    }

    /** All components of one row; balance = paid - fill - loss + sent - received (§8). */
    private static void assertRow(
            final MemberBalance row,
            final long memberId,
            final String paid,
            final String fill,
            final String loss,
            final String balance,
            final String provisional) {
        assertThat(row.memberId()).isEqualTo(memberId);
        assertThat(row.paidCosts()).as("paid of %d", memberId).isEqualByComparingTo(paid);
        assertThat(row.fillCharges()).as("fill of %d", memberId).isEqualByComparingTo(fill);
        assertThat(row.lossShares()).as("loss of %d", memberId).isEqualByComparingTo(loss);
        assertThat(row.paymentsSent()).isEqualByComparingTo("0");
        assertThat(row.paymentsReceived()).isEqualByComparingTo("0");
        assertThat(row.balance()).as("balance of %d", memberId).isEqualByComparingTo(balance);
        assertThat(row.provisionalCharges())
                .as("provisional of %d", memberId)
                .isEqualByComparingTo(provisional);
    }

    @Test
    void trueUpOpen() {
        final var rows =
                balances(
                        heliumFacts(
                                LossPolicy.TRUE_UP,
                                VesselStatus.IN_SERVICE,
                                List.of(),
                                Set.of(A, B, C)));

        // Balances A +540.00, B -180.00, C -120.00; the vessel is open TRUE_UP, so every fill
        // charge is still provisional.
        assertThat(rows).hasSize(3);
        assertRow(rows.get(0), A, "600.00", "60.00", "0", "540.00", "60.00");
        assertRow(rows.get(1), B, "0", "180.00", "0", "-180.00", "180.00");
        assertRow(rows.get(2), C, "0", "120.00", "0", "-120.00", "120.00");

        // Σ balances = 240.00 = unallocated; the inventory-backed creditor keeps 240.00.
        assertThat(rows.stream().map(MemberBalance::balance).reduce(BigDecimal::add).orElseThrow())
                .isEqualByComparingTo("240.00");
        assertThat(SettlementSuggester.suggest(rows))
                .containsExactly(transfer(B, A, "180.00"), transfer(C, A, "120.00"));
    }

    @Test
    void trueUpClosed() {
        final var rows =
                balances(
                        heliumFacts(
                                LossPolicy.TRUE_UP,
                                VesselStatus.CLOSED,
                                List.of(A, B, C),
                                Set.of(A, B, C)));

        // Balances A +500.00, B -300.00, C -200.00 (Σ = 0); nothing is provisional once closed.
        assertThat(rows).hasSize(3);
        assertRow(rows.get(0), A, "600.00", "100.00", "0", "500.00", "0");
        assertRow(rows.get(1), B, "0", "300.00", "0", "-300.00", "0");
        assertRow(rows.get(2), C, "0", "200.00", "0", "-200.00", "0");
        assertThat(SettlementSuggester.suggest(rows))
                .containsExactly(transfer(B, A, "300.00"), transfer(C, A, "200.00"));
    }

    @Test
    void pooledClosedWithFourthSharingMember() {
        final var rows =
                balances(
                        heliumFacts(
                                LossPolicy.POOLED,
                                VesselStatus.CLOSED,
                                List.of(A, B, C, D),
                                Set.of(A, B, C, D)));

        // Loss 300.00 split four ways = 75.00 each; D never filled but shares the loss.
        assertThat(rows).hasSize(4);
        assertRow(rows.get(0), A, "600.00", "50.00", "75.00", "475.00", "0");
        assertRow(rows.get(1), B, "0", "150.00", "75.00", "-225.00", "0");
        assertRow(rows.get(2), C, "0", "100.00", "75.00", "-175.00", "0");
        assertRow(rows.get(3), D, "0", "0", "75.00", "-75.00", "0");
        assertThat(SettlementSuggester.suggest(rows))
                .containsExactly(
                        transfer(B, A, "225.00"),
                        transfer(C, A, "175.00"),
                        transfer(D, A, "75.00"));
    }

    @Test
    void pooledClosedWithThreeParticipants() {
        final var rows =
                balances(
                        heliumFacts(
                                LossPolicy.POOLED,
                                VesselStatus.CLOSED,
                                List.of(A, B, C),
                                Set.of(A, B, C)));

        // Balances A +450.00, B -250.00, C -200.00 (Σ = 0).
        assertRow(rows.get(0), A, "600.00", "50.00", "100.00", "450.00", "0");
        assertRow(rows.get(1), B, "0", "150.00", "100.00", "-250.00", "0");
        assertRow(rows.get(2), C, "0", "100.00", "100.00", "-200.00", "0");
        assertThat(SettlementSuggester.suggest(rows))
                .containsExactly(transfer(B, A, "250.00"), transfer(C, A, "200.00"));
    }
}
