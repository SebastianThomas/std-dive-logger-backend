package ch.sthomas.stddivegastracker.model.team;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Flat stored facts of one team, read in one snapshot. {@code stages}: of non-voided fills, those
 * drawn from or filled into a vessel (AIR into a cylinder moves nothing); {@code members}: all.
 */
public record TeamFacts(
        List<VesselRow> vessels,
        List<CostLineRow> costLines,
        List<ReadingRow> readings,
        List<ParticipantRow> participants,
        List<StageRow> stages,
        List<Member> members) {

    public TeamFacts {
        vessels = List.copyOf(vessels);
        costLines = List.copyOf(costLines);
        readings = List.copyOf(readings);
        participants = List.copyOf(participants);
        stages = List.copyOf(stages);
        members = List.copyOf(members);
    }

    /** CLOSED means {@code closedAt != null}. */
    public record VesselRow(
            long vesselId,
            String label,
            double waterVolumeLitres,
            double mixO2,
            double mixHe,
            double initialO2,
            double initialHe,
            double initialPressureBar,
            double expectedResidualPressureBar,
            LossPolicy lossPolicy,
            Instant openedAt,
            @Nullable Instant closedAt,
            @Nullable Double closingPressureBar,
            @Nullable String notes,
            long createdByMemberId) {

        /** Declared mix (DOMAIN §5.1). */
        public GasMix mix() {
            return new GasMix(mixO2, mixHe);
        }

        /** The gas the vessel held when registered (DOMAIN §6 step 1). */
        public GasMix initialMix() {
            return new GasMix(initialO2, initialHe);
        }
    }

    public record CostLineRow(
            long vesselId,
            long costLineId,
            long payerMemberId,
            BigDecimal amount,
            Instant paidAt,
            @Nullable String note) {}

    public record ReadingRow(
            long vesselId,
            long readingId,
            double pressureBar,
            Instant measuredAt,
            long createdByMemberId) {}

    /** One member of the closing-participants snapshot of a closed vessel (DOMAIN §7.3). */
    public record ParticipantRow(long vesselId, long memberId) {}

    /**
     * One stage with its fill's time and target. A cylinder fill has {@code chargedMemberId}, a
     * vessel fill {@code targetVesselId}; AIR has no {@code sourceVesselId} (DOMAIN §5.4).
     */
    public record StageRow(
            long stageId,
            long fillId,
            Instant filledAt,
            @Nullable Long targetVesselId,
            @Nullable Long chargedMemberId,
            @Nullable Long sourceVesselId,
            double sourceO2,
            double sourceHe,
            double addedLitres,
            double addedO2Litres,
            double addedHeLitres,
            double lineLossLitres,
            double drawLitres) {

        /** Snapshotted source mix (DOMAIN §5.1). */
        public GasMix sourceMix() {
            return new GasMix(sourceO2, sourceHe);
        }
    }
}
