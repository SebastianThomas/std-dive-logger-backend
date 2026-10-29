package ch.sthomas.stddivegastracker.model.vessel;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

/**
 * API.md §3 {@code VesselUpdate}; an absent field is left as it is. Empty {@code notes} clears
 * them. {@code mix} and {@code lossPolicy} are ADMIN-only (checked by the service).
 */
public record VesselUpdate(
        @Nullable String label,
        @Nullable String notes,
        @Nullable Double expectedResidualPressureBar,
        @Nullable GasMix mix,
        @Nullable LossPolicy lossPolicy) {

    private static final int MAX_NOTES_LENGTH = 1000;

    public VesselUpdate {
        if (label != null) {
            label = Limits.label(label);
        }
        if (expectedResidualPressureBar != null) {
            Limits.pressureBar(expectedResidualPressureBar, "Residual pressure");
        }
        if (notes != null) {
            final var stripped = Limits.text(notes, MAX_NOTES_LENGTH, "Notes");
            notes = stripped == null ? "" : stripped;
        }
    }
}
