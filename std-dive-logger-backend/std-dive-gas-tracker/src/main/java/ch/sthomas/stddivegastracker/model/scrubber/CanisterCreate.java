package ch.sthomas.stddivegastracker.model.scrubber;

import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.model.vessel.Limits;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** API.md §7: a new canister; an absent capacity is the usual 23 kg, an absent opening now. */
public record CanisterCreate(
        String label,
        @Nullable Double capacityKg,
        @Nullable Instant openedAt,
        List<CostLineInput> initialCostLines) {

    private static final double USUAL_CAPACITY_KG = 23;

    public CanisterCreate {
        label = Limits.label(label);
        capacityKg = Limits.kg(capacityKg == null ? USUAL_CAPACITY_KG : capacityKg, "Capacity");
        initialCostLines = initialCostLines == null ? List.of() : List.copyOf(initialCostLines);
    }
}
