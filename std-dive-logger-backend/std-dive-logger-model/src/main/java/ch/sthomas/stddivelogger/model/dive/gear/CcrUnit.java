package ch.sthomas.stddivelogger.model.dive.gear;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

/**
 * A user-owned rebreather unit (e.g. "rEvo", "Poseidon SE7EN", a custom nickname). A dive can
 * reference up to two of these (see {@link DiveConfiguration#ccrUnit}/{@link
 * DiveConfiguration#secondaryCcrUnit}) for genuine dual-rebreather setups - independent of the
 * diver's own {@link BaseConfiguration}.
 */
public record CcrUnit(
        @Nullable @Positive Long id,
        @Positive long userId,
        @NotBlank String name,
        @NotNull String notes,
        boolean isPublic,
        // How this specific unit is normally worn - once set, a dive imported from a computer
        // linked to this unit (see DiveComputer.ccrUnitId) can infer the unit itself
        // automatically, see DiveService#inferConfigurationFromComputer. Purely informational
        // about the unit, not copied onto the dive's own BaseConfiguration.
        @Nullable CcrMountPosition mountPosition) {}
