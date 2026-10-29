package ch.sthomas.stddivegastracker.calc.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/** Builders and money assertions shared by the allocation tests. */
final class AllocationFixtures {

    static final long A = 1;
    static final long B = 2;
    static final long C = 3;
    static final long D = 4;

    private AllocationFixtures() {}

    static CostLine paid(final long payerMemberId, final String amount) {
        return new CostLine(payerMemberId, new BigDecimal(amount));
    }

    static Vessel vessel(
            final long vesselId,
            final LossPolicy policy,
            final VesselStatus status,
            final double inflowLitres,
            final double residualLitres,
            final List<CostLine> costLines,
            final Long... closingParticipantIds) {
        return new Vessel(
                vesselId,
                policy,
                status,
                costLines,
                inflowLitres,
                residualLitres,
                List.of(closingParticipantIds));
    }

    /** Draw into a member's cylinder. */
    static Draw fill(
            final long drawId,
            final long sourceVesselId,
            final double litres,
            final long memberId) {
        return new Draw(drawId, sourceVesselId, litres, new ToMember(memberId));
    }

    /** Draw into another vessel (internal transfer). */
    static Draw transfer(
            final long drawId,
            final long sourceVesselId,
            final double litres,
            final long targetVesselId) {
        return new Draw(drawId, sourceVesselId, litres, new ToVessel(targetVesselId));
    }

    static AllocationInput input(
            final List<Vessel> vessels, final List<Draw> draws, final Long... activeMemberIds) {
        return new AllocationInput(vessels, draws, Set.of(activeMemberIds));
    }

    /** Exact decimal value (the spec's literal), independent of scale. */
    static void assertMoney(final BigDecimal actual, final String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    /** For repeating decimals (e.g. 100/6000): compare after rounding to 10 places. */
    static void assertMoneyRounded(final BigDecimal actual, final String expected) {
        assertThat(actual.setScale(10, RoundingMode.HALF_EVEN)).isEqualByComparingTo(expected);
    }

    /** DOMAIN §8 without payments: Σ paid by the member − fill charges − loss shares. */
    static BigDecimal balance(
            final AllocationResult result, final long memberId, final String paidByMember) {
        final var member = result.member(memberId);
        return new BigDecimal(paidByMember)
                .subtract(member.fillCharges())
                .subtract(member.lossShares());
    }
}
