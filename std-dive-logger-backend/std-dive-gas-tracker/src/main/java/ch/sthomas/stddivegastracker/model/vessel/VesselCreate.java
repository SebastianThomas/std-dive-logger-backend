package ch.sthomas.stddivegastracker.model.vessel;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * API.md §3 {@code VesselCreate}. Absent optionals stay null: the data service applies the defaults
 * (0 bar, the team's residual pressure and loss policy, now). {@code mix} validates itself ({@link
 * GasMix}); a missing {@code waterVolumeLitres} is 0 and fails the range check. No initial cost
 * lines is an empty list; each one is a person who paid part of the bank.
 */
public record VesselCreate(
        String label,
        double waterVolumeLitres,
        GasMix mix,
        @Nullable Double initialPressureBar,
        @Nullable Double expectedResidualPressureBar,
        @Nullable LossPolicy lossPolicy,
        @Nullable Instant openedAt,
        @Nullable String notes,
        List<CostLineInput> initialCostLines) {

    private static final int MAX_NOTES_LENGTH = 1000;

    public VesselCreate {
        label = Limits.label(label);
        Limits.waterVolumeLitres(waterVolumeLitres);
        if (mix == null) {
            throw new IllegalArgumentException("Mix is required.");
        }
        if (initialPressureBar != null) {
            Limits.pressureBar(initialPressureBar, "Initial pressure");
        }
        if (expectedResidualPressureBar != null) {
            Limits.pressureBar(expectedResidualPressureBar, "Residual pressure");
        }
        notes = Limits.text(notes, MAX_NOTES_LENGTH, "Notes");
        initialCostLines = initialCostLines == null ? List.of() : List.copyOf(initialCostLines);
    }
}
