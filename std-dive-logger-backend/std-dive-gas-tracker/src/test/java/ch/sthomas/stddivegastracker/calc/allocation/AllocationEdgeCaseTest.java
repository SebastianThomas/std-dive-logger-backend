package ch.sthomas.stddivegastracker.calc.allocation;

import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.A;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.B;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.C;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.D;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.assertMoney;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.assertMoneyRounded;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.fill;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.input;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.paid;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.transfer;
import static ch.sthomas.stddivegastracker.calc.allocation.AllocationFixtures.vessel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.DrawCharge;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.MemberAllocation;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.VesselAllocation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

final class AllocationEdgeCaseTest {

    private static final long V1 = 1;
    private static final long V2 = 2;
    private static final long V3 = 3;

    // --- TRUE_UP closed with no draws: write-off (DOMAIN §7.3) ---

    @Test
    void trueUpClosedWithoutDrawsWritesTheCostOffEqually() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.CLOSED,
                                                6000,
                                                1000,
                                                List.of(paid(A, "600.00")),
                                                A,
                                                B,
                                                C)),
                                List.of(),
                                A,
                                B,
                                C));

        final var v = result.vessel(V1);
        assertMoney(v.rate(), "0");
        assertThat(v.provisional()).isFalse();
        assertMoney(v.drawnLitres(), "0");
        assertMoney(v.totalCost(), "600.00");
        assertMoney(v.allocated(), "600.00");
        assertMoney(v.unallocated(), "0");
        assertThat(v.lossShares()).extracting(LossShare::memberId).containsExactly(A, B, C);
        v.lossShares().forEach(share -> assertMoney(share.amount(), "200.00"));
        assertMoney(result.member(B).lossShares(), "200.00");
        assertMoney(result.member(B).fillCharges(), "0");
    }

    @Test
    void trueUpClosedWithOnlyZeroLitreDrawsIsAlsoAWriteOff() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.CLOSED,
                                                100,
                                                0,
                                                List.of(paid(A, "90.00")),
                                                A,
                                                B,
                                                C)),
                                List.of(fill(1, V1, 0, B)),
                                A,
                                B,
                                C));

        assertMoney(result.drawCharge(1).charge(), "0");
        result.vessel(V1).lossShares().forEach(share -> assertMoney(share.amount(), "30.00"));
        assertMoney(result.vessel(V1).unallocated(), "0");
    }

    // --- closing participant fallbacks (DOMAIN §7.3) ---

    /** TRUE_UP closed, no draws: a 500.00 write-off paid 200.00 by 5 and 300.00 by 6. */
    private static AllocationInput writeOff(
            final Set<Long> activeMembers, final Long... closingParticipants) {
        return new AllocationInput(
                List.of(
                        vessel(
                                V1,
                                LossPolicy.TRUE_UP,
                                VesselStatus.CLOSED,
                                1000,
                                0,
                                List.of(paid(5, "100.00"), paid(6, "300.00"), paid(5, "100.00")),
                                closingParticipants)),
                List.of(),
                activeMembers);
    }

    @Test
    void nonEmptySnapshotWinsOverActiveMembersAndPayers() {
        final var result = AllocationEngine.allocate(writeOff(Set.of(A, B, C, D), A, B));

        assertThat(result.vessel(V1).lossShares())
                .extracting(LossShare::memberId)
                .containsExactly(A, B);
        result.vessel(V1).lossShares().forEach(share -> assertMoney(share.amount(), "250.00"));
        // Active members that did not share still show up, with zero totals.
        assertMoney(result.member(C).lossShares(), "0");
        assertMoney(result.member(D).lossShares(), "0");
    }

    @Test
    void emptySnapshotFallsBackToActiveMembers() {
        final var result = AllocationEngine.allocate(writeOff(Set.of(C, D)));

        assertThat(result.vessel(V1).lossShares())
                .extracting(LossShare::memberId)
                .containsExactly(C, D);
        result.vessel(V1).lossShares().forEach(share -> assertMoney(share.amount(), "250.00"));
    }

    @Test
    void emptySnapshotAndNoActiveMembersFallBackToPayersProRata() {
        final var result = AllocationEngine.allocate(writeOff(Set.of()));

        final var shares = result.vessel(V1).lossShares();
        assertThat(shares).extracting(LossShare::memberId).containsExactly(5L, 6L);
        assertMoney(shares.get(0).amount(), "200.00"); // paid 200 of 500
        assertMoney(shares.get(1).amount(), "300.00"); // paid 300 of 500
        assertMoney(result.vessel(V1).unallocated(), "0");
        assertMoney(result.member(5).lossShares(), "200.00");
    }

    @Test
    void payerFallbackIsProRataNotEqualForUnequalPayments() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.CLOSED,
                                                1000,
                                                0,
                                                List.of(paid(5, "10.00"), paid(6, "30.00")))),
                                List.of(fill(1, V1, 500, A))));

        // POOLED rate 0.04, A pays 20.00, loss 20.00 split 1:3.
        assertMoney(result.vessel(V1).rate(), "0.04");
        final var shares = result.vessel(V1).lossShares();
        assertMoney(shares.get(0).amount(), "5.00");
        assertMoney(shares.get(1).amount(), "15.00");
    }

    @Test
    void noParticipantsNoActiveMembersAndNoPayersIsAnError() {
        // O's cost reaches X as an upstream charge, but nobody paid for X itself.
        final var in =
                input(
                        List.of(
                                vessel(
                                        V1,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.IN_SERVICE,
                                        1000,
                                        0,
                                        List.of(paid(A, "100.00"))),
                                vessel(
                                        V2,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.CLOSED,
                                        500,
                                        0,
                                        List.of())),
                        List.of(transfer(1, V1, 500, V2)));

        assertThatThrownBy(() -> AllocationEngine.allocate(in))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vessel " + V2);
    }

    @Test
    void payersWhoPaidNothingInTotalAreNotParticipants() {
        final var in =
                input(
                        List.of(
                                vessel(
                                        V1,
                                        LossPolicy.POOLED,
                                        VesselStatus.CLOSED,
                                        100,
                                        0,
                                        List.of(paid(A, "0.00")))),
                        List.of());

        assertThatThrownBy(() -> AllocationEngine.allocate(in))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vessel " + V1);
    }

    @Test
    void openVesselNeverNeedsParticipantsAndIgnoresAStaleSnapshot() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                0,
                                                List.of(paid(A, "10.00")),
                                                A,
                                                B)),
                                List.of()));

        assertThat(result.vessel(V1).lossShares()).isEmpty();
        assertMoney(result.vessel(V1).unallocated(), "10.00");
    }

    // --- POOLED ---

    @Test
    void pooledLossCanBeNegativeAndThenCreditsTheParticipants() {
        // V_in 500, drawn 600 (gauge error): rate 0.20, charges 120.00, loss -20.00.
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.CLOSED,
                                                500,
                                                0,
                                                List.of(paid(A, "100.00")),
                                                A,
                                                B)),
                                List.of(fill(1, V1, 600, B)),
                                A,
                                B));

        final var v = result.vessel(V1);
        assertMoney(v.rate(), "0.2");
        assertMoney(result.drawCharge(1).charge(), "120.00");
        v.lossShares().forEach(share -> assertMoney(share.amount(), "-10.00"));
        assertMoney(v.allocated(), "100.00");
        assertMoney(v.unallocated(), "0");
        assertMoney(result.member(A).lossShares(), "-10.00");
        assertMoney(result.member(B).fillCharges(), "120.00");
    }

    @Test
    void pooledWithoutInflowHasRateZero() {
        final var open =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.IN_SERVICE,
                                                0,
                                                0,
                                                List.of(paid(A, "50.00")))),
                                List.of(fill(1, V1, 10, B))));
        assertMoney(open.vessel(V1).rate(), "0");
        assertThat(open.vessel(V1).provisional()).isFalse();
        assertMoney(open.drawCharge(1).charge(), "0");
        assertMoney(open.vessel(V1).unallocated(), "50.00");

        final var closed =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.CLOSED,
                                                0,
                                                0,
                                                List.of(paid(A, "50.00")),
                                                A,
                                                B)),
                                List.of(fill(1, V1, 10, B))));
        assertMoney(closed.drawCharge(1).charge(), "0");
        closed.vessel(V1).lossShares().forEach(share -> assertMoney(share.amount(), "25.00"));
        assertMoney(closed.vessel(V1).unallocated(), "0");
    }

    // --- TRUE_UP open: max(V_in - V_res, D) ---

    @Test
    void trueUpOpenWithResidualAboveInflowAndNoDrawsHasRateZero() {
        for (final double residual : new double[] {100, 200}) {
            final var result =
                    AllocationEngine.allocate(
                            input(
                                    List.of(
                                            vessel(
                                                    V1,
                                                    LossPolicy.TRUE_UP,
                                                    VesselStatus.IN_SERVICE,
                                                    100,
                                                    residual,
                                                    List.of(paid(A, "100.00")))),
                                    List.of()));

            assertMoney(result.vessel(V1).rate(), "0");
            assertThat(result.vessel(V1).provisional()).isTrue();
            assertMoney(result.vessel(V1).unallocated(), "100.00");
        }
    }

    @Test
    void trueUpOpenWithResidualAboveInflowChargesByDrawnLitres() {
        // V_in - V_res = -100 <= D = 50 -> rate = 100 / 50.
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                200,
                                                List.of(paid(A, "100.00")))),
                                List.of(fill(1, V1, 50, B))));

        assertMoney(result.vessel(V1).rate(), "2");
        assertMoney(result.drawCharge(1).charge(), "100.00");
        assertMoney(result.vessel(V1).unallocated(), "0");
    }

    @Test
    void trueUpOpenUsesDrawnLitresWhenTheyExceedUsableContent() {
        // V_in - V_res = 5000 < D = 5500 -> rate 600 / 5500, everything allocated.
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                6000,
                                                1000,
                                                List.of(paid(A, "600.00")))),
                                List.of(fill(1, V1, 3000, B), fill(2, V1, 2500, C))));

        assertMoneyRounded(result.vessel(V1).rate(), "0.1090909091");
        assertMoneyRounded(result.drawCharge(1).charge(), "327.2727272727");
        assertMoneyRounded(result.vessel(V1).allocated(), "600.00");
        assertMoneyRounded(result.vessel(V1).unallocated(), "0");
    }

    // --- DAG ---

    @Test
    void chainIsEvaluatedSourcesFirstWhateverTheInputOrder() {
        // 3 -> 2 -> 1, rates all 0.10: V3 costs 100 and loses 50 in stock, V2 25, V1 0.
        final var vessel1 =
                vessel(V1, LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE, 250, 0, List.of());
        final var vessel2 =
                vessel(V2, LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE, 500, 0, List.of());
        final var vessel3 =
                vessel(
                        V3,
                        LossPolicy.TRUE_UP,
                        VesselStatus.IN_SERVICE,
                        1000,
                        0,
                        List.of(paid(A, "100.00")));
        final var draws =
                List.of(transfer(1, V3, 500, V2), transfer(2, V2, 250, V1), fill(3, V1, 250, B));

        final var result =
                AllocationEngine.allocate(input(List.of(vessel1, vessel2, vessel3), draws, A, B));
        final var reversed =
                AllocationEngine.allocate(
                        input(List.of(vessel3, vessel2, vessel1), draws.reversed(), B, A));

        assertThat(reversed).isEqualTo(result);
        assertThat(result.vessels())
                .extracting(VesselAllocation::vesselId)
                .containsExactly(V1, V2, V3);
        assertThat(result.drawCharges()).extracting(DrawCharge::drawId).containsExactly(1L, 2L, 3L);
        assertMoney(result.vessel(V3).rate(), "0.1");
        assertMoney(result.vessel(V2).totalCost(), "50.00");
        assertMoney(result.vessel(V1).totalCost(), "25.00");
        assertMoney(result.member(B).fillCharges(), "25.00");
        assertMoney(result.vessel(V3).unallocated(), "50.00");
        assertMoney(result.vessel(V2).unallocated(), "25.00");
        assertMoney(result.vessel(V1).unallocated(), "0");
    }

    // --- provisional: the price can still change (DOMAIN §7.1) ---

    @Test
    void openPooledVesselFedByAnOpenTrueUpVesselIsProvisional() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                1000,
                                                0,
                                                List.of(paid(A, "100.00"))),
                                        vessel(
                                                V2,
                                                LossPolicy.POOLED,
                                                VesselStatus.IN_SERVICE,
                                                500,
                                                0,
                                                List.of())),
                                List.of(transfer(1, V1, 500, V2), fill(2, V2, 100, B)),
                                A,
                                B));

        assertThat(result.vessel(V1).provisional()).isTrue();
        assertThat(result.vessel(V2).provisional()).isTrue();
        assertMoney(result.vessel(V2).rate(), "0.1"); // 50.00 / 500
        assertMoney(result.drawCharge(2).charge(), "10.00");
    }

    @Test
    void openPooledVesselWithoutTrueUpUpstreamIsNotProvisional() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.POOLED,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                0,
                                                List.of(paid(A, "10.00"))),
                                        vessel(
                                                V2,
                                                LossPolicy.POOLED,
                                                VesselStatus.IN_SERVICE,
                                                200,
                                                0,
                                                List.of()),
                                        vessel(
                                                V3,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.CLOSED,
                                                100,
                                                0,
                                                List.of(paid(A, "20.00")))),
                                List.of(
                                        transfer(1, V1, 50, V2),
                                        transfer(2, V3, 100, V2),
                                        fill(3, V2, 10, B)),
                                A,
                                B));

        assertThat(result.vessel(V1).provisional()).isFalse();
        assertThat(result.vessel(V2).provisional()).isFalse();
        assertThat(result.vessel(V3).provisional()).isFalse();
        assertMoney(result.vessel(V2).totalCost(), "25.00"); // 5.00 + 20.00
    }

    @Test
    void provisionalFollowsTransfersTransitively() {
        // Open TRUE_UP V3 -> closed POOLED V2 -> closed TRUE_UP V1: the whole chain re-prices.
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.CLOSED,
                                                250,
                                                0,
                                                List.of()),
                                        vessel(
                                                V2,
                                                LossPolicy.POOLED,
                                                VesselStatus.CLOSED,
                                                500,
                                                0,
                                                List.of(),
                                                A),
                                        vessel(
                                                V3,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                1000,
                                                0,
                                                List.of(paid(A, "100.00")))),
                                List.of(
                                        transfer(1, V3, 500, V2),
                                        transfer(2, V2, 250, V1),
                                        fill(3, V1, 100, B)),
                                A,
                                B));

        assertThat(result.vessels())
                .extracting(VesselAllocation::provisional)
                .containsExactly(true, true, true);
        assertMoney(result.vessel(V1).totalCost(), "25.00");
        assertMoney(result.drawCharge(3).charge(), "25.00"); // closed: B pays all of V1
    }

    @Test
    void provisionalDoesNotFlowUpstream() {
        // Closed TRUE_UP V1 feeds open TRUE_UP V2: only V2 can still change price.
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.CLOSED,
                                                100,
                                                0,
                                                List.of(paid(A, "10.00"))),
                                        vessel(
                                                V2,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                0,
                                                List.of())),
                                List.of(transfer(1, V1, 100, V2), fill(2, V2, 50, B)),
                                A,
                                B));

        assertThat(result.vessel(V1).provisional()).isFalse();
        assertThat(result.vessel(V2).provisional()).isTrue();
    }

    @Test
    void parallelTransfersBetweenTheSameVesselsAreNotACycle() {
        final var in =
                input(
                        List.of(
                                vessel(
                                        V1,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.CLOSED,
                                        100,
                                        0,
                                        List.of(paid(A, "10.00")),
                                        A),
                                vessel(
                                        V2,
                                        LossPolicy.POOLED,
                                        VesselStatus.IN_SERVICE,
                                        100,
                                        0,
                                        List.of())),
                        List.of(transfer(1, V1, 40, V2), transfer(2, V1, 60, V2)),
                        A);

        final var result = AllocationEngine.allocate(in);

        assertMoney(result.vessel(V2).totalCost(), "10.00");
    }

    @Test
    void mixedPoliciesAlongATransfer() {
        // TRUE_UP O (open) feeds POOLED X (closed, participants A and B).
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                9000,
                                                1000,
                                                List.of(paid(B, "300.00"))),
                                        vessel(
                                                V2,
                                                LossPolicy.POOLED,
                                                VesselStatus.CLOSED,
                                                6000,
                                                0,
                                                List.of(),
                                                A,
                                                B)),
                                List.of(
                                        transfer(1, V1, 2000, V2),
                                        fill(2, V1, 4000, A),
                                        fill(3, V2, 3000, C)),
                                A,
                                B,
                                C));

        assertMoney(result.vessel(V2).totalCost(), "75.00");
        assertMoney(result.vessel(V2).rate(), "0.0125");
        assertMoney(result.drawCharge(3).charge(), "37.50");
        result.vessel(V2).lossShares().forEach(share -> assertMoney(share.amount(), "18.75"));
        assertMoney(result.vessel(V2).unallocated(), "0");
        assertMoney(result.vessel(V1).unallocated(), "75.00");
        assertMoney(result.member(A).fillCharges(), "150.00");
        assertMoney(result.member(A).lossShares(), "18.75");
    }

    @Test
    void twoVesselCycleIsRejected() {
        final var in =
                input(
                        List.of(
                                vessel(
                                        V1,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.IN_SERVICE,
                                        100,
                                        0,
                                        List.of()),
                                vessel(
                                        V2,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.IN_SERVICE,
                                        100,
                                        0,
                                        List.of())),
                        List.of(transfer(1, V1, 10, V2), transfer(2, V2, 10, V1)));

        assertThatThrownBy(() -> AllocationEngine.allocate(in))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void threeVesselCycleBehindAnAcyclicPrefixIsRejected() {
        final var vessels =
                List.of(
                        vessel(
                                V1,
                                LossPolicy.POOLED,
                                VesselStatus.CLOSED,
                                100,
                                0,
                                List.of(paid(A, "1.00")),
                                A),
                        vessel(V2, LossPolicy.TRUE_UP, VesselStatus.IN_SERVICE, 100, 0, List.of()),
                        vessel(V3, LossPolicy.TRUE_UP, VesselStatus.CLOSED, 100, 0, List.of(), A),
                        vessel(4, LossPolicy.POOLED, VesselStatus.IN_SERVICE, 100, 0, List.of()));
        final var draws =
                List.of(
                        transfer(1, V1, 10, V2),
                        transfer(2, V2, 10, V3),
                        transfer(3, V3, 10, 4),
                        transfer(4, 4, 10, V2));

        assertThatThrownBy(() -> AllocationEngine.allocate(input(vessels, draws, A)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[2, 3, 4]");
    }

    // --- result shape ---

    @Test
    void membersCoverEveryMemberNamedInTheInputIncludingThoseWhoLeft() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                0,
                                                List.of(paid(7, "10.00")))),
                                List.of(fill(1, V1, 10, 9)),
                                A));

        assertThat(result.members())
                .extracting(MemberAllocation::memberId)
                .containsExactly(A, 7L, 9L);
        assertMoney(result.member(A).fillCharges(), "0");
        assertMoney(result.member(7).fillCharges(), "0");
        assertMoney(result.member(9).fillCharges(), "1.00");
    }

    @Test
    void lookupsFindByIdAndRejectUnknownIds() {
        final var result =
                AllocationEngine.allocate(
                        input(
                                List.of(
                                        vessel(
                                                V1,
                                                LossPolicy.TRUE_UP,
                                                VesselStatus.IN_SERVICE,
                                                100,
                                                0,
                                                List.of(paid(A, "10.00")))),
                                List.of(fill(5, V1, 10, B)),
                                A));

        assertThat(result.vessel(V1).vesselId()).isEqualTo(V1);
        assertThat(result.drawCharge(5).drawId()).isEqualTo(5);
        assertThat(result.member(B).memberId()).isEqualTo(B);
        assertThatThrownBy(() -> result.vessel(99))
                .isInstanceOf(AllocationInvariantException.class);
        assertThatThrownBy(() -> result.drawCharge(99))
                .isInstanceOf(AllocationInvariantException.class);
        assertThatThrownBy(() -> result.member(99))
                .isInstanceOf(AllocationInvariantException.class);
    }

    @Test
    void theResultIsSortedByIdSoLookupsFindEveryEntry() {
        final var result =
                new AllocationResult(
                        List.of(),
                        List.of(
                                new AllocationResult.DrawCharge(7, BigDecimal.ONE),
                                new AllocationResult.DrawCharge(2, BigDecimal.TEN),
                                new AllocationResult.DrawCharge(5, BigDecimal.TWO)),
                        List.of());

        assertThat(result.drawCharges())
                .extracting(AllocationResult.DrawCharge::drawId)
                .containsExactly(2L, 5L, 7L);
        assertThat(result.drawCharge(2).charge()).isEqualTo(BigDecimal.TEN);
        assertThat(result.drawCharge(7).charge()).isEqualTo(BigDecimal.ONE);
        assertThatThrownBy(() -> result.drawCharge(3))
                .isInstanceOf(AllocationInvariantException.class);
    }

    @Test
    void emptyInputGivesAnEmptyResult() {
        final var result = AllocationEngine.allocate(input(List.of(), List.of(), A));

        assertThat(result.vessels()).isEmpty();
        assertThat(result.drawCharges()).isEmpty();
        assertThat(result.members()).extracting(MemberAllocation::memberId).containsExactly(A);
    }

    @Test
    void sameInputAlwaysGivesTheSameResult() {
        final var in =
                input(
                        List.of(
                                vessel(
                                        V1,
                                        LossPolicy.TRUE_UP,
                                        VesselStatus.CLOSED,
                                        6000,
                                        1000,
                                        List.of(paid(A, "100.00")),
                                        A,
                                        B,
                                        C)),
                        List.of(fill(1, V1, 1500, B), fill(2, V1, 1000, C), fill(3, V1, 700, A)),
                        A,
                        B,
                        C);

        assertThat(AllocationEngine.allocate(in)).isEqualTo(AllocationEngine.allocate(in));
        assertThatCode(() -> AllocationEngine.allocate(in)).doesNotThrowAnyException();
    }
}
