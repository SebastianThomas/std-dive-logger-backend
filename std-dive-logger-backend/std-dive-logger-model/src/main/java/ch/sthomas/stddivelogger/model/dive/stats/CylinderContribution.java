package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderMaterial;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One tracked cylinder's line in the gas-consistency "show the working" breakdown - just enough to
 * let a diver eyeball whether a pressure or size was mistyped: {@code consumedLiters = (startBar -
 * endBar) x waterVolumeLiters}, null when either pressure is missing or the drop is non-positive.
 */
public record CylinderContribution(
        double waterVolumeLiters,
        @Nullable CylinderMaterial material,
        CylinderRole role,
        @Nullable Double startBar,
        @Nullable Double endBar,
        @Nullable Double consumedLiters,
        List<CylinderUsageWindow> usageWindows) {}
