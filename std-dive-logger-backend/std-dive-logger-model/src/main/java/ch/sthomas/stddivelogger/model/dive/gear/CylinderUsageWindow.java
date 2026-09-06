package ch.sthomas.stddivelogger.model.dive.gear;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * One stretch a cylinder was breathed over, measured from the earliest profile start. Either bound
 * {@code null} means unbounded on that side. A cylinder carries an ordered list of these - see
 * {@link DiveConfigurationCylinder#usageWindows()} for what an empty list means (the complement of
 * the same-role windowed cylinders).
 */
public record CylinderUsageWindow(@Nullable Duration start, @Nullable Duration end) {}
