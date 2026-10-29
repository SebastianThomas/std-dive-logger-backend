package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * What a fill stores (DOMAIN §5.4, no money): the service solves one, prices it in memory and hands
 * it to the data layer; the data layer returns it inside a {@link StoredFill}.
 */
public record FillContent(
        Instant filledAt,
        FillTarget target,
        double startPressureBar,
        GasMix startMix,
        double endPressureBar,
        GasMix endMix,
        @Nullable GasMix endAnalysis,
        @Nullable String notes,
        String calcModelVersion,
        List<Stage> stages) {

    public FillContent {
        stages = List.copyOf(stages);
    }

    /**
     * A null source vessel is AIR. {@code stageId} is negative in a draft ({@code -(index + 1)}).
     * {@code sourcePressureAfterBar}: the source bank's gauge after the stage as entered (null for
     * AIR); the data layer also keeps it as a reading of that bank.
     */
    public record Stage(
            long stageId,
            int stageIndex,
            @Nullable Long sourceVesselId,
            GasMix sourceMix,
            double pressureAfterBar,
            double addedLitres,
            double addedO2Litres,
            double addedHeLitres,
            double lineLossLitres,
            double drawLitres,
            @Nullable Double sourcePressureAfterBar) {}
}
