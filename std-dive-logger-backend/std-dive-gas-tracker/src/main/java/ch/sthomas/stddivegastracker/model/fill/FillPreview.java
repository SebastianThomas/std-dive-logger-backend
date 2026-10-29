package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import java.math.BigDecimal;
import java.util.List;

/**
 * API.md §4 {@code FillPreview}: what a fill computes to. {@code totalCharge} = round(Σ unrounded
 * stage charges); for a fill into a bank it is what the transfer costs.
 */
public record FillPreview(
        double endPressureBar,
        GasMix endMix,
        List<StageResult> stages,
        BigDecimal totalCharge,
        List<Warning> warnings) {

    public FillPreview {
        stages = List.copyOf(stages);
        warnings = List.copyOf(warnings);
    }
}
