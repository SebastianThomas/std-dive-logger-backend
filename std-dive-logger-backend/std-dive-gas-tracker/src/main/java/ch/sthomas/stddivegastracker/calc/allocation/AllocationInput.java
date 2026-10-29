package ch.sthomas.stddivegastracker.calc.allocation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Snapshot of one team (DOMAIN §7.1). {@code activeMemberIds} feeds the closing-participant
 * fallback of §7.3.
 */
public record AllocationInput(List<Vessel> vessels, List<Draw> draws, Set<Long> activeMemberIds) {

    public AllocationInput {
        vessels = List.copyOf(vessels);
        draws = List.copyOf(draws);
        activeMemberIds = Set.copyOf(activeMemberIds);

        final var vesselIds = new HashSet<Long>();
        for (final var vessel : vessels) {
            if (!vesselIds.add(vessel.vesselId())) {
                throw new IllegalArgumentException("Duplicate vessel id " + vessel.vesselId());
            }
        }
        final var drawIds = new HashSet<Long>();
        for (final var draw : draws) {
            if (!drawIds.add(draw.drawId())) {
                throw new IllegalArgumentException("Duplicate draw id " + draw.drawId());
            }
            if (!vesselIds.contains(draw.sourceVesselId())) {
                throw new IllegalArgumentException(
                        "Draw "
                                + draw.drawId()
                                + " draws from unknown vessel "
                                + draw.sourceVesselId());
            }
            if (draw.beneficiary() instanceof Beneficiary.ToVessel(final var targetId)
                    && !vesselIds.contains(targetId)) {
                throw new IllegalArgumentException(
                        "Draw " + draw.drawId() + " transfers into unknown vessel " + targetId);
            }
        }
    }

    /**
     * Engine view of a vessel (DOMAIN §7.1). {@code inflowLitres} is V_in, {@code residualLitres}
     * V_res. {@code closingParticipantIds} is the stored snapshot (closed vessels only, may be
     * empty).
     */
    public record Vessel(
            long vesselId,
            LossPolicy policy,
            VesselStatus status,
            List<CostLine> costLines,
            double inflowLitres,
            double residualLitres,
            List<Long> closingParticipantIds) {

        public Vessel {
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(status, "status");
            requireLitres(inflowLitres, "inflowLitres");
            requireLitres(residualLitres, "residualLitres");
            costLines = List.copyOf(costLines);
            closingParticipantIds = List.copyOf(closingParticipantIds);
            if (new HashSet<>(closingParticipantIds).size() != closingParticipantIds.size()) {
                throw new IllegalArgumentException(
                        "Vessel " + vesselId + " lists a closing participant twice");
            }
        }
    }

    /** Money paid for a vessel by a member (DOMAIN §2); the payer is credited in §8. */
    public record CostLine(long payerMemberId, BigDecimal amount) {

        public CostLine {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount must be >= 0");
            }
        }
    }

    /** Gas that left {@code sourceVesselId}; the unit of cost allocation (DOMAIN §2). */
    public record Draw(long drawId, long sourceVesselId, double litres, Beneficiary beneficiary) {

        public Draw {
            Objects.requireNonNull(beneficiary, "beneficiary");
            requireLitres(litres, "litres");
            if (beneficiary instanceof Beneficiary.ToVessel(final var targetId)
                    && targetId == sourceVesselId) {
                throw new IllegalArgumentException(
                        "Draw " + drawId + " cannot transfer into its own source vessel");
            }
        }
    }

    /** Who receives a draw: a member (cylinder fill) or another vessel (internal transfer). */
    public sealed interface Beneficiary {

        /** Draw into a diver's cylinder: the charge is a member fill charge. */
        record ToMember(long memberId) implements Beneficiary {}

        /** Draw into another vessel: the charge becomes part of its cost (DOMAIN §7.2). */
        record ToVessel(long vesselId) implements Beneficiary {}
    }

    private static void requireLitres(final double value, final String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite number >= 0");
        }
    }
}
