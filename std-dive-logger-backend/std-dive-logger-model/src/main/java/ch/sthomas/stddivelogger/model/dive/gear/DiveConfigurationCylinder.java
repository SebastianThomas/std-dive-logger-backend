package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A cylinder actually used on one dive, tracked well enough to compute real gas consumption: its
 * size and start/end pressure (litres consumed = pressure drop × cylinder volume), its {@link
 * CylinderMaterial} (descriptive only), the gas mix actually in it, its {@link CylinderRole}, and
 * which stretches of the dive it was breathed over.
 *
 * <p>{@code material} may be {@code null} on an incoming payload (legacy/imported rows) - the
 * persistence layer fills it via {@link StandardCylinder#inferMaterial}.
 *
 * <p>{@code usageWindows} is an ordered list. Empty means "used whenever the same-role windowed
 * cylinders aren't" - i.e. the complement of every other same-role cylinder's windows, which is the
 * whole dive when no same-role cylinder is windowed (the common single-cylinder case, no data entry
 * needed). See {@link ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionCalculator}.
 */
public record DiveConfigurationCylinder(
        long id,
        CylinderSize size,
        @Nullable CylinderMaterial material,
        @Nullable Double startBar,
        @Nullable Double endBar,
        String notes,
        Gas gas,
        CylinderRole role,
        @NotNull List<CylinderUsageWindow> usageWindows) {

    public DiveConfigurationCylinder {
        // Defensive against old/partial JSON that predates the field - a null list is "no windows".
        usageWindows = usageWindows == null ? List.of() : List.copyOf(usageWindows);
    }
}
