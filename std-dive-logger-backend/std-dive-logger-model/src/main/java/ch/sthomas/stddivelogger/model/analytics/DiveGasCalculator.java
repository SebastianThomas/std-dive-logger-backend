package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Computes the backend's own best-estimate PO2/FO2 for every measurement of every profile of a
 * dive, reasoning across all of the dive's profiles together rather than one at a time - a bailout
 * logged on one profile (e.g. a CCR handset) affects what every other profile's calculated values
 * look like at that same moment, not just the profile that recorded it. This is deliberately a
 * *different* number from a source device's own onboard calculation (see {@code
 * PO2Entity.calculated}, populated straight from an import file) - this one still produces a value
 * even for a profile whose own device never calculated one at all (e.g. a plain OC bailout
 * computer).
 *
 * <p>Priority per measurement, at its own time T on profile P:
 *
 * <ol>
 *   <li>Effective OC/CC mode at T = the mode last reported (held, not interpolated - a mode only
 *       changes at a discrete switch) at-or-before T across every profile of the dive that reports
 *       mode at all, not just P - most dives only have mode data on one profile (the CCR handset).
 *       No profile ever reports mode anywhere in the dive → treated as OC throughout.
 *   <li>CC: P's own measured PO2 at this exact sample, if its device has a PO2 sensor → else the
 *       handset profile's held setpoint at T → else computed as if OC (next step) from whichever
 *       profile actually has gas data - a last resort for "the loop isn't giving us anything, treat
 *       this like a bailout".
 *   <li>OC (or the dive never had CC in the first place): current breathing gas's O2 fraction (P's
 *       own held gas value; falls back to another profile's if P itself never logged one) × ambient
 *       pressure.
 * </ol>
 *
 * FO2 is always derived from PO2 (FO2 = PO2 / ambient pressure) rather than chained separately -
 * it's the same relationship regardless of which path produced PO2. A measurement this can't
 * produce any value for (no gas data anywhere in the whole dive) is simply left out of the result,
 * rather than fabricating a number.
 */
public final class DiveGasCalculator {

    // 10m of seawater ≈ 1 additional atmosphere - the same approximation used throughout this
    // codebase's other depth/pressure-adjacent calculations.
    private static final double METERS_PER_ATMOSPHERE = 10.0;

    private DiveGasCalculator() {}

    public record GasResult(long measurementId, double po2, double fo2) {}

    private record TimedValue<T>(Instant time, T value) {}

    public static List<GasResult> calculate(final List<DiveProfile> profiles) {
        final var modeTimeline = timeline(profiles, m -> m.measurement().mode());
        // "The CCR handset" - whichever profile actually reports mode, if any. At most one in
        // virtually every real dive; if somehow more than one does, the first (in the given
        // order) is used consistently for every lookup rather than picking differently each time.
        final var handset =
                profiles.stream()
                        .filter(p -> !timeline(List.of(p), m2 -> m2.measurement().mode()).isEmpty())
                        .findFirst();
        final var handsetSetpoints =
                handset
                        .map(h -> timeline(List.of(h), m -> m.measurement().po2()))
                        .orElse(List.of())
                        .stream()
                        .filter(tv -> tv.value().maxSetPoint() != null)
                        .map(tv -> new TimedValue<>(tv.time(), tv.value().maxSetPoint()))
                        .toList();

        final var results = new ArrayList<GasResult>();
        for (final var profile : profiles) {
            final var measurements = profile.measurements();
            if (measurements == null) {
                continue;
            }
            final var ownGas = gasTimeline(profile);
            for (final var m : measurements) {
                if (!Double.isFinite(m.measurement().depth())) {
                    continue;
                }
                final var time = m.measurement().time();
                final var ambient = ambientPressure(m.measurement().depth());
                final var mode =
                        heldAt(modeTimeline, time).map(TimedValue::value).orElse(DiveMode.OC);
                final Double po2 =
                        mode == DiveMode.CC
                                ? resolveCcPo2(m, handsetSetpoints, profiles, time, ambient)
                                : resolveOcPo2(ownGas, profiles, time, ambient);
                if (po2 == null) {
                    continue;
                }
                results.add(new GasResult(m.id(), po2, po2 / ambient));
            }
        }
        return results;
    }

    private static @Nullable Double resolveCcPo2(
            final DiveMeasurementWithId ownMeasurement,
            final List<TimedValue<Double>> handsetSetpoints,
            final List<DiveProfile> profiles,
            final Instant time,
            final double ambient) {
        final var ownPo2 = ownMeasurement.measurement().po2();
        if (ownPo2 != null && ownPo2.measured() != null) {
            return ownPo2.measured();
        }
        final var setpoint = heldAt(handsetSetpoints, time);
        if (setpoint.isPresent()) {
            return setpoint.get().value();
        }
        return resolveOcPo2FromAnyProfile(profiles, time, ambient);
    }

    private static @Nullable Double resolveOcPo2(
            final List<TimedValue<Double>> ownGas,
            final List<DiveProfile> profiles,
            final Instant time,
            final double ambient) {
        final var own = heldAt(ownGas, time);
        if (own.isPresent()) {
            return own.get().value() * ambient;
        }
        return resolveOcPo2FromAnyProfile(profiles, time, ambient);
    }

    /**
     * Same as {@link #resolveOcPo2}, but searches every profile's gas timeline in turn - used as
     * the final fallback both when OC-computing normally and when CC has nothing else to go on.
     * {@code ambient} is always the *querying* measurement's own ambient pressure, even when the
     * FO2 value itself comes from another profile's held sample - gas fraction doesn't depend on
     * depth, so borrowing it from elsewhere is fine, but the PO2 it implies right now depends on
     * how deep *this* measurement actually is.
     */
    private static @Nullable Double resolveOcPo2FromAnyProfile(
            final List<DiveProfile> profiles, final Instant time, final double ambient) {
        for (final var other : profiles) {
            final var held = heldAt(gasTimeline(other), time);
            if (held.isPresent()) {
                return held.get().value() * ambient;
            }
        }
        return null;
    }

    private static double ambientPressure(final double depthMeters) {
        return 1.0 + depthMeters / METERS_PER_ATMOSPHERE;
    }

    private static <T> List<TimedValue<T>> timeline(
            final List<DiveProfile> profiles,
            final Function<DiveMeasurementWithId, @Nullable T> extractor) {
        final var result = new ArrayList<TimedValue<T>>();
        for (final var profile : profiles) {
            final var measurements = profile.measurements();
            if (measurements == null) {
                continue;
            }
            for (final var m : measurements) {
                final var value = extractor.apply(m);
                if (value != null) {
                    result.add(new TimedValue<>(m.measurement().time(), value));
                }
            }
        }
        result.sort(Comparator.comparing(TimedValue::time));
        return result;
    }

    private static List<TimedValue<Double>> gasTimeline(final DiveProfile profile) {
        final var result = new ArrayList<TimedValue<Double>>();
        for (final var tv : timeline(List.of(profile), m -> m.measurement().gas())) {
            result.add(new TimedValue<>(tv.time(), tv.value().o2()));
        }
        return result;
    }

    /**
     * The last value at-or-before {@code time} in a list sorted ascending by time - a value only
     * changes at a discrete event (a gas switch, a setpoint change, a mode switch) and holds until
     * the next one, so "nearest" would be wrong whenever the *next* sample happens to be closer in
     * time than the one actually in effect right now. Falls back to the very first sample when
     * {@code time} is before every recorded sample, rather than returning nothing for the start of
     * a profile.
     */
    private static <T> Optional<TimedValue<T>> heldAt(
            final List<TimedValue<T>> sorted, final Instant time) {
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        var lo = 0;
        var hi = sorted.size();
        while (lo < hi) {
            final var mid = (lo + hi) / 2;
            if (sorted.get(mid).time().isAfter(time)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return Optional.of(lo > 0 ? sorted.get(lo - 1) : sorted.getFirst());
    }
}
