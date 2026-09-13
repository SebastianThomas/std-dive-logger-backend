package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Relates a cylinder's usage windows to the profile they were taken from. A window starts where a
 * profile switched onto that cylinder's mix (or where the profile starts on it), so when that
 * profile alone is re-aligned in time, the window has to move with it.
 */
public final class CylinderUsageWindows {

    /** How far a window's start may be from the gas switch it corresponds to. */
    static final Duration SWITCH_TOLERANCE = Duration.ofMinutes(2);

    private static final double FRACTION_TOLERANCE = 0.005;

    private CylinderUsageWindows() {}

    /**
     * Whether {@code window} (relative to {@code diveStart}) starts at a point where the profile
     * breathes {@code cylinderGas} for the first time or switches onto it. An open start (breathed
     * from the dive's start) belongs to no single profile and never matches.
     */
    public static boolean startsAtGasSwitch(
            final Gas cylinderGas,
            final CylinderUsageWindow window,
            final Instant diveStart,
            final List<DiveMeasurement> profileMeasurements) {
        if (window.start() == null) {
            return false;
        }
        final var windowStart = diveStart.plus(window.start());
        Gas previous = null;
        for (final var measurement : profileMeasurements) {
            final var gas = measurement.gas();
            if (gas == null || (previous != null && sameMix(previous, gas))) {
                continue;
            }
            previous = gas;
            if (sameMix(gas, cylinderGas)
                    && Duration.between(measurement.time(), windowStart)
                                    .abs()
                                    .compareTo(SWITCH_TOLERANCE)
                            <= 0) {
                return true;
            }
        }
        return false;
    }

    static boolean sameMix(final Gas a, final Gas b) {
        return Math.abs(a.o2() - b.o2()) <= FRACTION_TOLERANCE
                && Math.abs(a.he() - b.he()) <= FRACTION_TOLERANCE;
    }
}
