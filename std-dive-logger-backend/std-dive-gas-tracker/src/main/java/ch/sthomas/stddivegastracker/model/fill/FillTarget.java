package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.model.vessel.Limits;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/**
 * API.md §4 target of a fill, on input and output (DOMAIN §5.1): a diver's cylinder, a vessel (its
 * water volume applies), or gas taken for a member and known only from the bank's pressure drop
 * (DOMAIN §5.7). {@code vesselLabel} is output only.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FillTarget.Cylinder.class, name = "CYLINDER"),
    @JsonSubTypes.Type(value = FillTarget.Vessel.class, name = "VESSEL"),
    @JsonSubTypes.Type(value = FillTarget.BankDraw.class, name = "BANK_DRAW")
})
@Schema(
        oneOf = {FillTarget.Cylinder.class, FillTarget.Vessel.class, FillTarget.BankDraw.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "CYLINDER", schema = FillTarget.Cylinder.class),
            @DiscriminatorMapping(value = "VESSEL", schema = FillTarget.Vessel.class),
            @DiscriminatorMapping(value = "BANK_DRAW", schema = FillTarget.BankDraw.class)
        })
public sealed interface FillTarget {

    /**
     * An absent {@code chargedMemberId} is 0, which no member has (400 "Unknown member" in the
     * service).
     */
    @Schema(name = "FillTargetCylinder")
    record Cylinder(double waterVolumeLitres, @Nullable String cylinderLabel, long chargedMemberId)
            implements FillTarget {

        private static final int MAX_LABEL_LENGTH = 60;

        public Cylinder {
            Limits.waterVolumeLitres(waterVolumeLitres);
            cylinderLabel = Limits.text(cylinderLabel, MAX_LABEL_LENGTH, "Cylinder label");
        }
    }

    /** An absent {@code vesselId} is 0, which no vessel has (400 "Unknown bank" in the service). */
    @Schema(name = "FillTargetVessel")
    record Vessel(long vesselId, @Nullable String vesselLabel) implements FillTarget {}

    /** An absent {@code chargedMemberId} is 0: 400 "Unknown member", as for a cylinder. */
    @Schema(name = "FillTargetBankDraw")
    record BankDraw(long chargedMemberId) implements FillTarget {}

    /** The member a fill is charged to; a fill into a vessel has none. */
    static @Nullable Long chargedMemberOf(final FillTarget target) {
        return switch (target) {
            case Cylinder(_, _, final var memberId) -> memberId;
            case Vessel _ -> null;
            case BankDraw(final var memberId) -> memberId;
        };
    }
}
