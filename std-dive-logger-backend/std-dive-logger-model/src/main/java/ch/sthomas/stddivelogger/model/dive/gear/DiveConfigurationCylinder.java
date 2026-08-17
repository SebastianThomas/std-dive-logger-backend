package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A cylinder actually used on one dive, tracked well enough to compute real gas consumption: its
 * size and start/end pressure (litres consumed = pressure drop × cylinder volume), the gas mix
 * actually in it ({@code gas.size()}/{@code gas.content()}/{@code gas.description()} are unused
 * here - this cylinder's own {@code size} and {@code notes} already cover that), its {@link
 * CylinderRole}, and optionally which stretch of the dive it was breathed from.
 *
 * <p>{@code usageStart}/{@code usageEnd} are both {@code null} for the common case - a single
 * cylinder used for the whole dive - which needs no extra data entry at all. Set them only when
 * more than one cylinder of the same role was used across the dive (e.g. twin/sidemount cylinders
 * switched partway through, or a bailout stage only breathed during part of an ascent), so
 * consumption can be weighted against the correct portion of the profile rather than the whole
 * dive's average depth.
 */
public record DiveConfigurationCylinder(
        long id,
        CylinderSize size,
        @Nullable Double startBar,
        @Nullable Double endBar,
        String notes,
        Gas gas,
        CylinderRole role,
        @Nullable Instant usageStart,
        @Nullable Instant usageEnd) {}
