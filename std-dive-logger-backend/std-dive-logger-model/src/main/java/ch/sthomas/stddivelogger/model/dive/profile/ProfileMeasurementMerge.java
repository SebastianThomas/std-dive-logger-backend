package ch.sthomas.stddivelogger.model.dive.profile;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Combines two recordings of the same profile - e.g. a dive computer's UDDF and native XML export
 * of one dive - into one that loses nothing either carries: every sample either file has, and at a
 * sample both have, every field either one filled in.
 *
 * <p>The result doesn't depend on which recording came first. Where both carry a value for the same
 * field of the same sample and the values differ, the <em>richer</em> recording (more filled in
 * fields overall) wins - a property of the data, not of the order it was imported in.
 */
public final class ProfileMeasurementMerge {

    /** Samples of the two recordings closer than this are the same sample of the dive. */
    static final Duration SAME_SAMPLE = Duration.ofSeconds(1);

    private ProfileMeasurementMerge() {}

    public static List<DiveMeasurement> merge(
            final List<DiveMeasurement> a, final List<DiveMeasurement> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        final var aWins = compareRichness(a, b) >= 0;
        final var primary = sortedByTime(aWins ? a : b);
        final var secondary = sortedByTime(aWins ? b : a);

        final var merged = new ArrayList<DiveMeasurement>(primary.size() + secondary.size());
        var i = 0;
        var j = 0;
        while (i < primary.size() || j < secondary.size()) {
            if (j >= secondary.size()) {
                merged.add(primary.get(i++));
            } else if (i >= primary.size()) {
                merged.add(secondary.get(j++));
            } else {
                final var p = primary.get(i);
                final var s = secondary.get(j);
                final var gap = Duration.between(p.time(), s.time());
                if (gap.abs().compareTo(SAME_SAMPLE) <= 0) {
                    merged.add(combine(p, s));
                    i++;
                    j++;
                } else if (gap.isNegative()) {
                    merged.add(s);
                    j++;
                } else {
                    merged.add(p);
                    i++;
                }
            }
        }
        return merged;
    }

    /** {@code p}'s values, with every field {@code p} lacks filled in from {@code s}. */
    static DiveMeasurement combine(final DiveMeasurement p, final DiveMeasurement s) {
        return new DiveMeasurement(
                p.time(),
                first(p.temperature(), s.temperature()),
                Double.isFinite(p.depth()) ? p.depth() : s.depth(),
                first(p.ndl(), s.ndl()),
                p.deco() != null && !p.deco().isEmpty() ? p.deco() : s.deco(),
                first(p.gas(), s.gas()),
                combinePo2(p.po2(), s.po2()),
                first(p.rmvLiters(), s.rmvLiters()),
                first(p.n2(), s.n2()),
                first(p.o2Tox(), s.o2Tox()),
                first(p.cns(), s.cns()),
                first(p.mode(), s.mode()),
                first(p.timeToSurface(), s.timeToSurface()));
    }

    private static @Nullable PO2 combinePo2(final @Nullable PO2 p, final @Nullable PO2 s) {
        if (p == null || s == null) {
            return p != null ? p : s;
        }
        return new PO2(
                first(p.maxSetPoint(), s.maxSetPoint()),
                first(p.measured(), s.measured()),
                first(p.calculated(), s.calculated()));
    }

    private static <T> @Nullable T first(final @Nullable T preferred, final @Nullable T fallback) {
        return preferred != null ? preferred : fallback;
    }

    /**
     * Positive when {@code a} is the richer recording. Ties fall through to more samples, then to a
     * fingerprint of the samples themselves, so the answer is the same whichever way round the two
     * are passed in.
     */
    private static int compareRichness(
            final List<DiveMeasurement> a, final List<DiveMeasurement> b) {
        final var byFields = Long.compare(filledFields(a), filledFields(b));
        if (byFields != 0) {
            return byFields;
        }
        final var bySize = Integer.compare(a.size(), b.size());
        if (bySize != 0) {
            return bySize;
        }
        return Integer.compare(fingerprint(a), fingerprint(b));
    }

    private static long filledFields(final List<DiveMeasurement> measurements) {
        return measurements.stream()
                .mapToLong(
                        m ->
                                count(m.temperature())
                                        + count(m.ndl())
                                        + (m.deco() != null && !m.deco().isEmpty() ? 1 : 0)
                                        + count(m.gas())
                                        + count(m.po2())
                                        + count(m.rmvLiters())
                                        + count(m.n2())
                                        + count(m.o2Tox())
                                        + count(m.cns())
                                        + count(m.mode())
                                        + count(m.timeToSurface()))
                .sum();
    }

    private static int count(final @Nullable Object value) {
        return value != null ? 1 : 0;
    }

    // Times and depths only - stable across JVM runs, unlike an enum's identity hash.
    private static int fingerprint(final List<DiveMeasurement> measurements) {
        var hash = 1;
        for (final var m : measurements) {
            hash = 31 * hash + Objects.hash(m.time(), m.depth());
        }
        return hash;
    }

    private static List<DiveMeasurement> sortedByTime(final List<DiveMeasurement> measurements) {
        return measurements.stream().sorted(Comparator.comparing(DiveMeasurement::time)).toList();
    }
}
