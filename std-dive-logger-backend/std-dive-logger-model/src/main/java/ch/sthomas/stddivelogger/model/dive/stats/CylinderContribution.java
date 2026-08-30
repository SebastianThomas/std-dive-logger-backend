package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderMaterial;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One tracked cylinder's line in the gas-consistency "show the working" breakdown - enough to let a
 * diver eyeball whether a pressure or size was mistyped, and to see each open-circuit cylinder's
 * own RMV (e.g. bottom stage vs deco stage).
 *
 * <ul>
 *   <li>{@code consumedLiters = (startBar - endBar) x waterVolumeLiters}, null when either pressure
 *       is missing or the drop is non-positive.
 *   <li>{@code pressureMinutes} / {@code rmvLiters} are populated only for a cylinder actually
 *       breathed open-circuit (OC on an OC dive, BAILOUT on a CCR dive - never O2/Diluent, which
 *       are injected into a loop, not breathed). {@code rmvLiters = consumedLiters /
 *       pressureMinutes} over this cylinder's own {@link #effectiveWindows}. Per-cylinder RMVs
 *       deliberately do not sum to the combined figure when cylinders share a window (the combined
 *       RMV uses the union).
 *   <li>{@code effectiveWindows} = the cylinder's explicit {@code usageWindows} if it has any, else
 *       the computed complement of the same-role windowed cylinders (empty + {@code
 *       coversWholeDive} when it genuinely spans the whole mode-gated dive).
 * </ul>
 */
public record CylinderContribution(
        double waterVolumeLiters,
        @Nullable CylinderMaterial material,
        CylinderRole role,
        @Nullable Double startBar,
        @Nullable Double endBar,
        @Nullable Double consumedLiters,
        List<CylinderUsageWindow> usageWindows,
        @Nullable Double pressureMinutes,
        @Nullable Double rmvLiters,
        List<CylinderUsageWindow> effectiveWindows,
        boolean coversWholeDive) {}
