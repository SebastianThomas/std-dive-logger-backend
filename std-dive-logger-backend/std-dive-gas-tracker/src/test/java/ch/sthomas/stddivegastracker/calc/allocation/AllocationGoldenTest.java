package ch.sthomas.stddivegastracker.calc.allocation;

import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.A;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.B;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.C;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.D;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.assertMoney;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.assertMoneyRounded;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.balance;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.fill;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.input;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.paid;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.transfer;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.MemberAllocation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

/** Golden values of DOMAIN §11.4 - §11.6 (literal numbers from the spec). */
final class AllocationGoldenTest {

    private static final long HELIUM = 10;
    private static final long OXYGEN = 20;
    private static final long HOUSE_MIX = 21;

    /**
     * §11.4 / §11.5 facts: H costs 600.00 (paid by A), V_in 6000, V_res 1000; B 1500, C 1000, A
     * 500.
     */
    private static AllocationInput heliumFacts(
            final LossPolicy policy,
            final VesselStatus status,
            final List<Long> closingParticipants,
            final Long... activeMembers) {
        return input(
                List.of(
                        vessel(
                                HELIUM,
                                policy,
                                status,
                                6000,
                                1000,
                                List.of(paid(A, "600.00")),
                                closingParticipants.toArray(new Long[0]))),
                List.of(
                        fill(1, HELIUM, 1500, B),
                        fill(2, HELIUM, 1000, C),
                        fill(3, HELIUM, 500, A)),
                activeMembers);
    }

    // --- §11.4 TRUE_UP ---

    @Test
    void trueUpOpen() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(
                                LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE, List.of(), A, B, C));

        final var h = result.vessel(HELIUM);
        assertMoney(h.rate(), "0.12");
        assertThat(h.provisional()).isTrue(); // open TRUE_UP
        assertMoney(h.totalCost(), "600.00");
        assertMoney(h.drawnLitres(), "3000");
        assertMoney(h.allocated(), "360.00");
        assertMoney(h.unallocated(), "240.00");
        assertThat(h.lossShares()).isEmpty();

        assertMoney(result.drawCharge(1).charge(), "180.00"); // B
        assertMoney(result.drawCharge(2).charge(), "120.00"); // C
        assertMoney(result.drawCharge(3).charge(), "60.00"); // A
        assertMoney(result.member(B).fillCharges(), "180.00");
        assertMoney(result.member(C).fillCharges(), "120.00");
        assertMoney(result.member(A).fillCharges(), "60.00");

        // Balances A +540.00, B -180.00, C -120.00; Σ = 240.00 = unallocated.
        assertMoney(balance(result, A, "600.00"), "540.00");
        assertMoney(balance(result, B, "0"), "-180.00");
        assertMoney(balance(result, C, "0"), "-120.00");
    }

    @Test
    void trueUpClosed() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(
                                LossPolicy.TRUE_UP,
                                VesselStatus.CLOSED,
                                List.of(A, B, C),
                                A,
                                B,
                                C));

        final var h = result.vessel(HELIUM);
        assertMoney(h.rate(), "0.20");
        assertThat(h.provisional()).isFalse(); // closed TRUE_UP
        assertMoney(h.totalCost(), "600.00");
        assertMoney(h.allocated(), "600.00");
        assertMoney(h.unallocated(), "0");
        assertThat(h.lossShares()).isEmpty();

        assertMoney(result.drawCharge(1).charge(), "300.00"); // B
        assertMoney(result.drawCharge(2).charge(), "200.00"); // C
        assertMoney(result.drawCharge(3).charge(), "100.00"); // A
        assertMoney(result.member(B).fillCharges(), "300.00");
        assertMoney(result.member(C).fillCharges(), "200.00");
        assertMoney(result.member(A).fillCharges(), "100.00");

        // Balances A +500.00, B -300.00, C -200.00 (Σ = 0).
        assertMoney(balance(result, A, "600.00"), "500.00");
        assertMoney(balance(result, B, "0"), "-300.00");
        assertMoney(balance(result, C, "0"), "-200.00");
    }

    // --- §11.5 POOLED ---

    @Test
    void pooledOpen() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(
                                LossPolicy.POOLED, VesselStatus.IN_SERVICE, List.of(), A, B, C));

        final var h = result.vessel(HELIUM);
        assertMoney(h.rate(), "0.10");
        assertThat(h.provisional()).isFalse(); // POOLED, no TRUE_UP upstream
        assertMoney(h.drawnLitres(), "3000");
        assertMoney(h.allocated(), "300.00");
        assertMoney(h.unallocated(), "300.00");
        assertThat(h.lossShares()).isEmpty();

        assertMoney(result.drawCharge(1).charge(), "150.00"); // B
        assertMoney(result.drawCharge(2).charge(), "100.00"); // C
        assertMoney(result.drawCharge(3).charge(), "50.00"); // A
        assertMoney(result.member(B).fillCharges(), "150.00");
        assertMoney(result.member(C).fillCharges(), "100.00");
        assertMoney(result.member(A).fillCharges(), "50.00");
    }

    @Test
    void pooledClosedWithThreeParticipants() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(
                                LossPolicy.POOLED, VesselStatus.CLOSED, List.of(A, B, C), A, B, C));

        final var h = result.vessel(HELIUM);
        assertMoney(h.rate(), "0.10");
        assertMoney(h.allocated(), "600.00");
        assertMoney(h.unallocated(), "0");
        assertThat(h.lossShares()).extracting(LossShare::memberId).containsExactly(A, B, C);
        h.lossShares().forEach(share -> assertMoney(share.amount(), "100.00"));

        // Balances A +450.00, B -250.00, C -200.00 (Σ = 0).
        assertMoney(balance(result, A, "600.00"), "450.00");
        assertMoney(balance(result, B, "0"), "-250.00");
        assertMoney(balance(result, C, "0"), "-200.00");
    }

    @Test
    void pooledClosedWithFourthSharingMemberFromSnapshot() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(
                                LossPolicy.POOLED,
                                VesselStatus.CLOSED,
                                List.of(A, B, C, D),
                                A,
                                B,
                                C,
                                D));

        assertFourMemberBalances(result);
    }

    @Test
    void pooledClosedWithFourthSharingMemberFromActiveMemberFallback() {
        final var result =
                AllocationEngine.allocate(
                        heliumFacts(LossPolicy.POOLED, VesselStatus.CLOSED, List.of(), A, B, C, D));

        assertFourMemberBalances(result);
    }

    private static void assertFourMemberBalances(final AllocationResult result) {
        final var h = result.vessel(HELIUM);
        assertThat(h.lossShares()).extracting(LossShare::memberId).containsExactly(A, B, C, D);
        h.lossShares().forEach(share -> assertMoney(share.amount(), "75.00"));
        assertMoney(h.unallocated(), "0");

        // D never filled but still carries a loss share: A +475.00, B -225.00, C -175.00, D -75.00.
        assertMoney(result.member(D).fillCharges(), "0");
        assertMoney(balance(result, A, "600.00"), "475.00");
        assertMoney(balance(result, B, "0"), "-225.00");
        assertMoney(balance(result, C, "0"), "-175.00");
        assertMoney(balance(result, D, "0"), "-75.00");
    }

    // --- §11.6 house mix propagation, TRUE_UP ---

    /**
     * O: 300.00 paid by B, V_in 9000, V_res 1000. X: no cost, V_in 6000, V_res 0. O -> X 2000 L, O
     * -> A 4000 L, X -> C 3000 L. Only O's status varies; X stays in service.
     */
    private static AllocationInput houseMixFacts(final VesselStatus oxygenStatus) {
        return input(
                List.of(
                        vessel(
                                OXYGEN,
                                LossPolicy.TRUE_UP,
                                oxygenStatus,
                                9000,
                                1000,
                                List.of(paid(B, "300.00")),
                                A,
                                B,
                                C),
                        vessel(
                                HOUSE_MIX,
                                LossPolicy.TRUE_UP,
                                VesselStatus.IN_SERVICE,
                                6000,
                                0,
                                List.of())),
                List.of(
                        transfer(1, OXYGEN, 2000, HOUSE_MIX),
                        fill(2, OXYGEN, 4000, A),
                        fill(3, HOUSE_MIX, 3000, C)),
                A,
                B,
                C);
    }

    @Test
    void houseMixOpen() {
        final var result = AllocationEngine.allocate(houseMixFacts(VesselStatus.IN_SERVICE));

        final var o = result.vessel(OXYGEN);
        assertMoney(o.drawnLitres(), "6000");
        assertMoney(o.rate(), "0.0375"); // 300 / max(8000, 6000)
        assertThat(o.provisional()).isTrue();
        assertMoney(o.unallocated(), "75.00");

        assertMoney(result.drawCharge(1).charge(), "75.00"); // internal transfer
        assertMoney(result.drawCharge(2).charge(), "150.00"); // A

        final var x = result.vessel(HOUSE_MIX);
        assertMoney(x.totalCost(), "75.00");
        assertMoney(x.rate(), "0.0125"); // 75 / max(6000, 3000)
        assertMoney(result.drawCharge(3).charge(), "37.50"); // C
        assertMoney(x.unallocated(), "37.50");
        assertThat(x.provisional()).isTrue(); // open, and fed by the open O

        // The transfer is not a member charge.
        assertMoney(result.member(A).fillCharges(), "150.00");
        assertMoney(result.member(C).fillCharges(), "37.50");
        assertMoney(result.member(B).fillCharges(), "0");
        assertMoney(o.unallocated().add(x.unallocated()).add(sumFillCharges(result)), "300.00");
    }

    @Test
    void houseMixWithSourceClosed() {
        final var result = AllocationEngine.allocate(houseMixFacts(VesselStatus.CLOSED));

        final var o = result.vessel(OXYGEN);
        assertMoney(o.rate(), "0.05"); // 300 / 6000
        assertThat(o.provisional()).isFalse();
        assertMoney(o.unallocated(), "0");
        assertThat(o.lossShares()).isEmpty();

        assertMoney(result.drawCharge(1).charge(), "100.00");
        assertMoney(result.drawCharge(2).charge(), "200.00"); // A

        final var x = result.vessel(HOUSE_MIX);
        assertMoney(x.totalCost(), "100.00");
        assertMoneyRounded(x.rate(), "0.0166666667"); // 100 / 6000
        assertThat(x.provisional()).isTrue(); // X itself is open TRUE_UP
        assertMoneyRounded(result.drawCharge(3).charge(), "50.00"); // C
        assertMoneyRounded(x.unallocated(), "50.00");

        // Conservation: 300 = 200 (A) + 50 (C) + unallocated(X) 50.
        assertMoneyRounded(x.unallocated().add(sumFillCharges(result)), "300.00");
    }

    @Test
    void houseMixWithBothClosedRepricesTheMixToItsOwnDraws() {
        final var facts = houseMixFacts(VesselStatus.CLOSED);
        final var closedMix =
                new Vessel(
                        HOUSE_MIX,
                        LossPolicy.TRUE_UP,
                        VesselStatus.CLOSED,
                        List.of(),
                        6000,
                        0,
                        List.of(A, B, C));
        final var result =
                AllocationEngine.allocate(
                        new AllocationInput(
                                List.of(facts.vessels().getFirst(), closedMix),
                                facts.draws(),
                                facts.activeMemberIds()));

        final var x = result.vessel(HOUSE_MIX);
        assertMoneyRounded(x.rate(), "0.0333333333"); // 100 / 3000
        assertThat(x.provisional()).isFalse(); // closed, fed by a closed source
        assertMoneyRounded(result.drawCharge(3).charge(), "100.00"); // C pays the whole mix
        assertMoneyRounded(x.unallocated(), "0");
        assertMoneyRounded(sumFillCharges(result), "300.00");
    }

    @Test
    void closedHouseMixFedByAnOpenSourceIsStillProvisional() {
        final var facts = houseMixFacts(VesselStatus.IN_SERVICE);
        final var closedMix =
                new Vessel(
                        HOUSE_MIX,
                        LossPolicy.TRUE_UP,
                        VesselStatus.CLOSED,
                        List.of(),
                        6000,
                        0,
                        List.of(A, B, C));
        final var result =
                AllocationEngine.allocate(
                        new AllocationInput(
                                List.of(facts.vessels().getFirst(), closedMix),
                                facts.draws(),
                                facts.activeMemberIds()));

        // O open: X costs 75.00; X closed with D = 3000: rate 0.025 -> C pays all 75.00.
        assertThat(result.vessel(OXYGEN).provisional()).isTrue();
        final var x = result.vessel(HOUSE_MIX);
        assertMoney(x.totalCost(), "75.00");
        assertMoney(x.rate(), "0.025");
        assertMoney(result.drawCharge(3).charge(), "75.00");
        assertMoney(x.unallocated(), "0");
        assertThat(x.provisional()).isTrue(); // the price follows O's re-pricing
    }

    private static BigDecimal sumFillCharges(final AllocationResult result) {
        return result.members().stream()
                .map(MemberAllocation::fillCharges)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
