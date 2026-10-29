package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.team.MemberRef;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * API.md §4 {@code Fill} = {@link FillPreview} fields + the stored header (flat, as the JSON is). A
 * voided fill costs nothing: its charges are "0.00".
 */
public record Fill(
        double endPressureBar,
        GasMix endMix,
        List<StageResult> stages,
        BigDecimal totalCharge,
        List<Warning> warnings,
        long fillId,
        Instant filledAt,
        FillTarget target,
        @Nullable MemberRef chargedMember,
        MemberRef recordedBy,
        double startPressureBar,
        GasMix startMix,
        @Nullable GasMix endAnalysis,
        @Nullable String notes,
        FillStatus status,
        @Nullable String voidReason,
        String calcModelVersion,
        Instant createdAt,
        Instant updatedAt) {

    public Fill {
        stages = List.copyOf(stages);
        warnings = List.copyOf(warnings);
    }
}
