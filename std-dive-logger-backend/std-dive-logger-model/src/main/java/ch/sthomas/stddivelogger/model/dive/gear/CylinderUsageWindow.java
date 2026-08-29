package ch.sthomas.stddivelogger.model.dive.gear;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One stretch of a dive a cylinder was actually breathed over. Either bound {@code null} means
 * unbounded on that side. A cylinder carries an ordered list of these - see {@link
 * DiveConfigurationCylinder#usageWindows()} for what an empty list means (the complement of the
 * same-role windowed cylinders).
 */
public record CylinderUsageWindow(@Nullable Instant start, @Nullable Instant end) {}
