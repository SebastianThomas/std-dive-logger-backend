package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.vessel.Limits;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * API.md §4 {@code FillInput} for preview, create and update (DOMAIN §5.1). Absent optionals stay
 * null: the service applies the defaults (now, AIR or the bank's mix). Ids are checked against the
 * team there; the pressures' order is checked by the solver.
 */
public record FillInput(
        @Nullable Instant filledAt,
        FillTarget target,
        Double startPressureBar,
        @Nullable GasMix startMix,
        List<Stage> stages,
        @Nullable GasMix endAnalysis,
        @Nullable String notes) {

    private static final int MAX_NOTES_LENGTH = 1000;
    private static final int MAX_STAGES = 10;

    public FillInput {
        if (target == null) {
            throw new IllegalArgumentException("Target is required.");
        }
        startPressureBar = Limits.pressureBar(startPressureBar, "Start pressure");
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("A fill needs at least one stage.");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("A fill has at most " + MAX_STAGES + " stages.");
        }
        stages = List.copyOf(stages);
        notes = Limits.text(notes, MAX_NOTES_LENGTH, "Notes");
    }

    /**
     * {@code sourcePressureAfterBar}: the bank's gauge after this stage, kept as a reading of that
     * bank (ignored for AIR).
     */
    @Schema(name = "FillInputStage")
    public record Stage(
            StageSource source, Double pressureAfterBar, @Nullable Double sourcePressureAfterBar) {

        public Stage {
            if (source == null) {
                throw new IllegalArgumentException("Every stage needs a source.");
            }
            pressureAfterBar = Limits.pressureBar(pressureAfterBar, "Stage pressure");
            if (sourcePressureAfterBar != null) {
                Limits.pressureBar(sourcePressureAfterBar, "Bank pressure");
            }
        }
    }
}
