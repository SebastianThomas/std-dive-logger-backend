package ch.sthomas.stddivelogger.model.analytics;

import static ch.sthomas.stddivelogger.model.analytics.TimedHold.heldAt;
import static ch.sthomas.stddivelogger.model.analytics.TimedHold.timeline;

import ch.sthomas.stddivelogger.model.analytics.TimedHold.TimedValue;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.stats.CylinderContribution;

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
 *
 * <p>A cylinder with no usage window of its own is treated as the <b>complement</b> of the
 * same-role windowed cylinders - it was breathed over exactly the parts of the dive those don't
 * cover (the whole dive when none are windowed). See {@link #combinedRmv}.
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
        final var modeTimeline = timeline(profiles, m -> m.measurement().mode());
        final var isCcrDive = modeTimeline.stream().anyMatch(tv -> tv.value() == DiveMode.CC);

        if (depthTimeline.isEmpty()) {
            // No profile depth data: litres only, no RMV / pressure-minutes.
            return new CylinderConsumptionResult(
                    null,
                    null,
                    sumConsumedLiters(cylinders, CylinderRole.O2),
                    sumConsumedLiters(cylinders, CylinderRole.DILUENT),
                    sumConsumedLiters(cylinders, CylinderRole.OC),
                    null,
                    contributionsFor(cylinders, List.of(), null, isCcrDive));
        }

        final var ocRmv =
                isCcrDive
                        ? RoleRmv.NONE
                        : combinedRmv(cylinders, CylinderRole.OC, depthTimeline, null);
        final var bailoutRmv =
                isCcrDive
                        ? combinedRmv(cylinders, CylinderRole.BAILOUT, depthTimeline, modeTimeline)
                        : RoleRmv.NONE;
        final var o2Liters = sumConsumedLiters(cylinders, CylinderRole.O2);
        final var diluentLiters = sumConsumedLiters(cylinders, CylinderRole.DILUENT);
        final var ocConsumedLiters = sumConsumedLiters(cylinders, CylinderRole.OC);

        return new CylinderConsumptionResult(
                ocRmv.rmvLiters(),
                bailoutRmv.rmvLiters(),
                o2Liters,
                diluentLiters,
                ocConsumedLiters,
                ocRmv.rmvLiters() == null ? null : ocRmv.pressureMinutes(),
                contributionsFor(cylinders, depthTimeline, modeTimeline, isCcrDive));
    }

    /**
     * Per-cylinder "show the working" lines. Litres for every cylinder; per-cylinder RMV +
     * pressure-minutes + effective windows only for a cylinder actually breathed open-circuit (OC
     * on an OC dive, BAILOUT on a CCR dive). A cylinder with no explicit window is the complement
     * of its role's windowed cylinders - {@code effectiveWindows} then carries the computed
     * complement interval(s), or is empty + {@code coversWholeDive} when it genuinely spans the
     * whole (mode-gated) dive.
     */
    private static List<CylinderContribution> contributionsFor(
            final List<DiveConfigurationCylinder> cylinders,
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final boolean isCcrDive) {
        return cylinders.stream()
                .map(c -> contributionFor(c, cylinders, depthTimeline, modeTimeline, isCcrDive))
                .toList();
    }

    private static CylinderContribution contributionFor(
            final DiveConfigurationCylinder cylinder,
            final List<DiveConfigurationCylinder> allCylinders,
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final boolean isCcrDive) {
        final var consumed = consumedLiters(cylinder);
        final var role = cylinder.role();
        final var breathesOpenCircuit =
                (!isCcrDive && role == CylinderRole.OC)
                        || (isCcrDive && role == CylinderRole.BAILOUT);

        Double pressureMinutes = null;
        Double rmvLiters = null;
        List<CylinderUsageWindow> effectiveWindows = cylinder.usageWindows();
        var coversWholeDive = false;

        if (breathesOpenCircuit && consumed != null && !depthTimeline.isEmpty()) {
            final var modeGate = role == CylinderRole.BAILOUT ? modeTimeline : null;
            if (!cylinder.usageWindows().isEmpty()) {
                final var own = cylinder.usageWindows().stream().map(UsageWindow::of).toList();
                pressureMinutes = pressureMinutesCovered(depthTimeline, modeGate, own);
            } else {
                final var sameRoleExplicit = explicitWindowsForRole(allCylinders, role);
                pressureMinutes =
                        pressureMinutesNotCovered(depthTimeline, modeGate, sameRoleExplicit);
                if (sameRoleExplicit.isEmpty()) {
                    coversWholeDive = true;
                    effectiveWindows = List.of();
                } else {
                    effectiveWindows =
                            complementIntervals(depthTimeline, modeGate, sameRoleExplicit);
                }
            }
            if (pressureMinutes > 0) {
                rmvLiters = consumed / pressureMinutes;
            } else {
                pressureMinutes = null;
            }
        }

        return new CylinderContribution(
                cylinder.size().liters(),
                cylinder.material(),
                role,
                cylinder.startBar(),
                cylinder.endBar(),
                consumed,
                cylinder.usageWindows(),
                pressureMinutes,
                rmvLiters,
                effectiveWindows,
                coversWholeDive);
    }

    /** Every explicit usage window across the windowed, pressure-usable cylinders of one role. */
    private static List<UsageWindow> explicitWindowsForRole(
            final List<DiveConfigurationCylinder> cylinders, final CylinderRole role) {
        return cylinders.stream()
                .filter(
                        c ->
                                c.role() == role
                                        && consumedLiters(c) != null
                                        && !c.usageWindows().isEmpty())
                .flatMap(c -> c.usageWindows().stream())
                .map(UsageWindow::of)
                .toList();
    }

    /**
     * The contiguous stretches of {@code depthTimeline} inside <b>none</b> of {@code windows} (and,
     * with {@code modeTimeline}, at {@link DiveMode#OC}) - the intervals an unwindowed cylinder was
     * actually the one being breathed. Mirrors {@link #pressureMinutesNotCovered} but returns the
     * merged intervals rather than their pressure-minutes.
     */
    private static List<CylinderUsageWindow> complementIntervals(
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final List<UsageWindow> windows) {
        final var result = new ArrayList<CylinderUsageWindow>();
        Instant runStart = null;
        Instant runEnd = null;
        for (var i = 0; i + 1 < depthTimeline.size(); i++) {
            final var a = depthTimeline.get(i);
            final var b = depthTimeline.get(i + 1);
            final var inSomeWindow =
                    windows.stream().anyMatch(w -> segmentWithinWindow(a.time(), b.time(), w));
            final var okMode =
                    modeTimeline == null
                            || heldAt(modeTimeline, a.time()).map(TimedValue::value).orElse(null)
                                    == DiveMode.OC;
            final var include =
                    !inSomeWindow && okMode && durationInMinutes(a.time(), b.time()) > 0;
            if (include) {
                if (runStart == null) {
                    runStart = a.time();
                }
                runEnd = b.time();
            } else if (runStart != null) {
                result.add(new CylinderUsageWindow(runStart, runEnd));
                runStart = null;
            }
        }
        if (runStart != null) {
            result.add(new CylinderUsageWindow(runStart, runEnd));
        }
        return result;
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

    /** A cylinder's usage window - either bound {@code null} means unbounded on that side. */
    private record UsageWindow(@Nullable Instant start, @Nullable Instant end) {
        static UsageWindow of(final CylinderUsageWindow w) {
            return new UsageWindow(w.start(), w.end());
        }
    }

    /**
     * One role's combined RMV plus the pressure-minutes denominator behind it (for the
     * gas-consistency breakdown). {@code rmvLiters} null = nothing to compute it from.
     */
    private record RoleRmv(@Nullable Double rmvLiters, double pressureMinutes) {
        static final RoleRmv NONE = new RoleRmv(null, 0);
    }

    /**
     * Combined RMV for one role. Cylinders split into <b>windowed</b> (≥1 usage window - active
     * over the union of their own windows) and <b>unwindowed</b> (active over the
     * <i>complement</i>: every part of the (mode-gated) profile not covered by any windowed
     * cylinder of this role; the whole dive when no cylinder of the role is windowed).
     *
     * <p>RMV = Σ litres of every contributing cylinder ÷ (pressure-minutes over the union of all
     * windowed cylinders' windows + the complement's pressure-minutes when ≥1 unwindowed cylinder
     * contributes) - the two ranges are disjoint by construction, so the denominator just adds.
     *
     * @param modeTimeline when non-null, only segments held at {@link DiveMode#OC} count towards
     *     pressure-minutes (used for bailout RMV); {@code null} means every segment counts.
     */
    private static RoleRmv combinedRmv(
            final List<DiveConfigurationCylinder> cylinders,
            final CylinderRole role,
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline) {
        final var windowed = new ArrayList<DiveConfigurationCylinder>();
        final var unwindowed = new ArrayList<DiveConfigurationCylinder>();
        for (final var cylinder : cylinders) {
            if (cylinder.role() != role || consumedLiters(cylinder) == null) {
                continue;
            }
            (cylinder.usageWindows().isEmpty() ? unwindowed : windowed).add(cylinder);
        }
        if (windowed.isEmpty() && unwindowed.isEmpty()) {
            return RoleRmv.NONE;
        }

        final var explicitWindows =
                windowed.stream()
                        .flatMap(c -> c.usageWindows().stream())
                        .map(UsageWindow::of)
                        .toList();
        // Every (mode-gated) profile segment not inside any windowed cylinder's window. Empty
        // explicitWindows => the whole (mode-gated) profile.
        final var complementPressureMinutes =
                pressureMinutesNotCovered(depthTimeline, modeTimeline, explicitWindows);

        var numerator = 0.0;
        var anyIncluded = false;
        for (final var cylinder : windowed) {
            final var liters = consumedLiters(cylinder);
            if (liters == null) {
                continue;
            }
            final var ownWindows = cylinder.usageWindows().stream().map(UsageWindow::of).toList();
            // Same gate as before: a windowed cylinder whose own windows cover no pressure-minutes
            // (doesn't overlap the profile) contributes nothing.
            if (pressureMinutesCovered(depthTimeline, modeTimeline, ownWindows) <= 0) {
                continue;
            }
            numerator += liters;
            anyIncluded = true;
        }
        var anyUnwindowedIncluded = false;
        for (final var cylinder : unwindowed) {
            final var liters = consumedLiters(cylinder);
            if (liters == null) {
                continue;
            }
            // Drop an unwindowed cylinder when the complement covers 0 pressure-minutes - the
            // windowed cylinders already span the whole dive, so a real pressure drop here is
            // contradictory data; excluding its litres is safer than inflating RMV against zero
            // added time.
            if (complementPressureMinutes <= 0) {
                continue;
            }
            numerator += liters;
            anyIncluded = true;
            anyUnwindowedIncluded = true;
        }
        if (!anyIncluded) {
            return RoleRmv.NONE;
        }

        final var denominator =
                pressureMinutesCovered(depthTimeline, modeTimeline, explicitWindows)
                        + (anyUnwindowedIncluded ? complementPressureMinutes : 0.0);
        return new RoleRmv(denominator > 0 ? numerator / denominator : null, denominator);
    }

    private static double pressureMinutesCovered(
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final List<UsageWindow> windows) {
        return pressureMinutes(depthTimeline, modeTimeline, windows, false);
    }

    private static double pressureMinutesNotCovered(
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final List<UsageWindow> windows) {
        return pressureMinutes(depthTimeline, modeTimeline, windows, true);
    }

    /**
     * Sums (segment duration in minutes × average ambient pressure) across every consecutive pair
     * of samples in {@code depthTimeline} that falls within <b>any</b> of {@code windows} ({@code
     * invert == false}) or within <b>none</b> of them ({@code invert == true}) and, when {@code
     * modeTimeline} is given, whose held mode at the segment's start is {@link DiveMode#OC}. An
     * empty {@code windows} list covers nothing ({@code invert == false}) / everything ({@code
     * invert == true}).
     */
    private static double pressureMinutes(
            final List<TimedValue<Double>> depthTimeline,
            final @Nullable List<TimedValue<DiveMode>> modeTimeline,
            final List<UsageWindow> windows,
            final boolean invert) {
        var total = 0.0;
        for (var i = 0; i + 1 < depthTimeline.size(); i++) {
            final var a = depthTimeline.get(i);
            final var b = depthTimeline.get(i + 1);
            final var within =
                    windows.stream().anyMatch(w -> segmentWithinWindow(a.time(), b.time(), w));
            if (within == invert) {
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
