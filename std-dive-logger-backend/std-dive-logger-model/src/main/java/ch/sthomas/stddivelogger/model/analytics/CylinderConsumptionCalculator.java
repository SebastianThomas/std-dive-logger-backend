package ch.sthomas.stddivelogger.model.analytics;

import static ch.sthomas.stddivelogger.model.analytics.TimedHold.heldAt;
import static ch.sthomas.stddivelogger.model.analytics.TimedHold.timeline;

import ch.sthomas.stddivelogger.model.analytics.TimedHold.TimedValue;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes real gas-consumption figures from a dive's tracked {@code DiveConfigurationCylinder}s,
 * instead of the whole-dive SAC/RMV a source device (or {@code DiveGasConsumption}) might report -
 * that whole-dive figure is meaningless for a CCR dive (there's no continuous open-circuit
 * breathing rate for a closed loop) and, even for OC, was previously either entirely absent or a
 * single number that couldn't account for different cylinders used across different portions of the
 * dive.
 *
 * <p>A plain OC dive's {@link CylinderRole#OC} cylinder(s) combine into one whole-dive RMV. A CCR
 * dive splits differently, since "RMV" isn't one meaningful number there:
 *
 * <ul>
 *   <li>{@link CylinderRole#BAILOUT} cylinders combine into a bailout RMV, computed only across the
 *       portions of the dive actually spent open-circuit (per-measurement {@code mode}, same signal
 *       {@link DiveGasCalculator} already uses to distinguish CC from OC) - the loop being breathed
 *       the rest of the time doesn't dilute it.
 *   <li>{@link CylinderRole#O2} and {@link CylinderRole#DILUENT} cylinders are summed into plain
 *       litres injected, not a rate - there's no "per minute" breathing concept for gas added to a
 *       closed loop to maintain setpoint/volume.
 * </ul>
 *
 * RMV is computed as total litres consumed ÷ total "pressure-minutes" (across every consecutive
 * pair of measurements that qualify, that segment's duration in minutes × its average ambient
 * pressure) - the correct denominator, since a minute spent deep costs more gas at a given
 * breathing rate than a minute spent shallow. Multiple cylinders of the same role always combine
 * their litres by summing (gas draw from simultaneous cylinders is genuinely additive), but their
 * pressure-minutes combine by <b>union</b>, not by summing each cylinder's own window
 * independently: two cylinders with disjoint usage windows (a sequential twinset switch) union to
 * the same total as summing would give, but two cylinders sharing the same (or overlapping) window
 * - e.g. true simultaneous doubles on one manifold, the common case of no usage window set on
 * either - represent the *same* elapsed dive time, not twice as much of it. Summing pressure-
 * minutes for that case double-counts the denominator and understates RMV by roughly half.
 */
public final class CylinderConsumptionCalculator {

    private static final double METERS_PER_ATMOSPHERE = 10.0;

    private CylinderConsumptionCalculator() {}

    public static CylinderConsumptionResult calculate(
            final List<DiveProfile> profiles, final List<DiveConfigurationCylinder> cylinders) {
        if (cylinders.isEmpty()) {
            return CylinderConsumptionResult.EMPTY;
        }
        final var depthTimeline = timeline(profiles, m -> m.measurement().depth());
        if (depthTimeline.isEmpty()) {
            return CylinderConsumptionResult.EMPTY;
        }
        final var modeTimeline = timeline(profiles, m -> m.measurement().mode());
        final var isCcrDive = modeTimeline.stream().anyMatch(tv -> tv.value() == DiveMode.CC);

        final var ocRmv =
                isCcrDive ? null : combinedRmv(cylinders, CylinderRole.OC, depthTimeline, null);
        final var bailoutRmv =
                isCcrDive
                        ? combinedRmv(cylinders, CylinderRole.BAILOUT, depthTimeline, modeTimeline)
                        : null;
        final var o2Liters = sumConsumedLiters(cylinders, CylinderRole.O2);
        final var diluentLiters = sumConsumedLiters(cylinders, CylinderRole.DILUENT);

        return new CylinderConsumptionResult(ocRmv, bailoutRmv, o2Liters, diluentLiters);
    }

    private static @Nullable Double consumedLiters(final DiveConfigurationCylinder cylinder) {
        if (cylinder.startBar() == null || cylinder.endBar() == null) {
            return null;
        }
        final var pressureDrop = cylinder.startBar() - cylinder.endBar();
        if (pressureDrop <= 0) {
            return null;
        }
        return pressureDrop * cylinder.size().liters();
    }

    private static @Nullable Double sumConsumedLiters(
            final List<DiveConfigurationCylinder> cylinders, final CylinderRole role) {
        var total = 0.0;
        var any = false;
        for (final var cylinder : cylinders) {
            if (cylinder.role() != role) {
                continue;
            }
            final var liters = consumedLiters(cylinder);
            if (liters == null) {
                continue;
            }
            total += liters;
            any = true;
        }
        return any ? total : null;
    }

    /**
     * A cylinder's usage window - either bound {@code null} means unbounded on that side, i.e.
     * "used for the whole dive".
     */
    private record UsageWindow(@Nullable Instant start, @Nullable Instant end) {}

    /**
     * @param modeTimeline when non-null, only segments held at {@link DiveMode#OC} count towards
     *     the pressure-minutes denominator (used for bailout RMV); {@code null} means every segment
     *     counts (a plain OC dive's own cylinders).
     */
    private static @Nullable Double combinedRmv(
            final List<DiveConfigurationCylinder> cylinders,
            final CylinderRole role,
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline) {
        var totalLiters = 0.0;
        final var windows = new ArrayList<UsageWindow>();
        for (final var cylinder : cylinders) {
            if (cylinder.role() != role) {
                continue;
            }
            final var liters = consumedLiters(cylinder);
            if (liters == null) {
                continue;
            }
            final var window = new UsageWindow(cylinder.usageStart(), cylinder.usageEnd());
            // Gate on this cylinder's own window actually covering some of the dive, same as
            // before - a cylinder whose window doesn't overlap the profile at all contributes
            // neither litres nor pressure-minutes.
            if (pressureMinutesCovered(depthTimeline, modeTimeline, List.of(window)) <= 0) {
                continue;
            }
            totalLiters += liters;
            windows.add(window);
        }
        if (windows.isEmpty()) {
            return null;
        }
        // The combined denominator is the union of every contributing cylinder's window, not the
        // sum of each computed independently - see this class's own doc comment for why summing
        // double-counts elapsed time whenever two cylinders share (or overlap) a window.
        final var totalPressureMinutes =
                pressureMinutesCovered(depthTimeline, modeTimeline, windows);
        return totalPressureMinutes > 0 ? totalLiters / totalPressureMinutes : null;
    }

    /**
     * Sums (segment duration in minutes × average ambient pressure) across every consecutive pair
     * of samples in {@code depthTimeline} that falls within <b>any</b> of {@code windows} (a
     * segment covered by more than one window still counts only once) and, when {@code
     * modeTimeline} is given, whose held mode at the segment's start is {@link DiveMode#OC}.
     */
    private static double pressureMinutesCovered(
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final List<UsageWindow> windows) {
        var total = 0.0;
        for (var i = 0; i + 1 < depthTimeline.size(); i++) {
            final var a = depthTimeline.get(i);
            final var b = depthTimeline.get(i + 1);
            if (windows.stream().noneMatch(w -> segmentWithinWindow(a.time(), b.time(), w))) {
                continue;
            }
            if (modeTimeline != null) {
                final var mode = heldAt(modeTimeline, a.time()).map(TimedValue::value).orElse(null);
                if (mode != DiveMode.OC) {
                    continue;
                }
            }
            final var durationMinutes = durationInMinutes(a.time(), b.time());
            if (durationMinutes <= 0) {
                continue;
            }
            final var avgAmbient = (ambientPressure(a.value()) + ambientPressure(b.value())) / 2;
            total += durationMinutes * avgAmbient;
        }
        return total;
    }

    private static boolean segmentWithinWindow(
            final Instant segmentStart, final Instant segmentEnd, final UsageWindow window) {
        if (window.start() != null && segmentStart.isBefore(window.start())) {
            return false;
        }
        return window.end() == null || !segmentEnd.isAfter(window.end());
    }

    private static double durationInMinutes(final Instant start, final Instant end) {
        return java.time.Duration.between(start, end).toMillis() / 60_000.0;
    }

    private static double ambientPressure(final double depthMeters) {
        return 1.0 + Math.max(0, depthMeters) / METERS_PER_ATMOSPHERE;
    }
}
