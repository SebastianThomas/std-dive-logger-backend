package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public record PO2(
        @Nullable Double maxSetPoint, @Nullable Double measured, @Nullable Double calculated) {
    @Contract(
            "null, null, null -> null; !null, _, _ -> !null; _, !null, _ -> !null; _, _, !null -> !null")
    @Nullable
    public static PO2 fromOrNull(
            @Nullable final Double setPO2,
            @Nullable final Double measuredPO2,
            @Nullable final Double calcPO2) {
        if (setPO2 == null && measuredPO2 == null && calcPO2 == null) {
            return null;
        }
        return new PO2(setPO2, measuredPO2, calcPO2);
    }
}
