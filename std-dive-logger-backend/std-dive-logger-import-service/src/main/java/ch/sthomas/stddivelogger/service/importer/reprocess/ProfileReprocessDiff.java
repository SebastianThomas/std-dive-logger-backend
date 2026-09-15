package ch.sthomas.stddivelogger.service.importer.reprocess;

import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * How a profile re-derived from its stored files differs from the stored one. The proposal is
 * compared as it would be stored (whole NDL minutes, whole TTS seconds, ...), so an unchanged file
 * never reads as a change; see {@code ProfileReprocessDiffTest}.
 */
public final class ProfileReprocessDiff {

    public enum Outcome {
        IDENTICAL,
        /** Only values or samples the stored profile didn't have - safe to apply unasked. */
        ADDITIONS_ONLY,
        CHANGED
    }

    public record Result(Outcome outcome, String summary) {}

    private static final Duration SAME_SAMPLE = Duration.ofMillis(500);
    private static final Duration MIN_CLOCK_CHANGE = Duration.ofSeconds(1);

    private enum Value {
        DEPTH("depth"),
        TEMPERATURE("temperature"),
        NDL("NDL"),
        DECO("deco stops"),
        GAS("gas"),
        PO2("PO2"),
        RMV("RMV"),
        N2("N2 loading"),
        O2_TOX("O2 toxicity"),
        CNS("CNS"),
        MODE("dive mode"),
        TTS("TTS");

        private final String label;

        Value(final String label) {
            this.label = label;
        }
    }

    private static final List<Map.Entry<String, Function<DecoSettings, @Nullable Object>>>
            DECO_FIELDS =
                    List.of(
                            Map.entry("algorithm", DecoSettings::algorithm),
                            Map.entry("implementation", DecoSettings::implementation),
                            Map.entry("GF low", DecoSettings::gfLow),
                            Map.entry("GF high", DecoSettings::gfHigh),
                            Map.entry("conservatism", DecoSettings::conservatism),
                            Map.entry("surface pressure", DecoSettings::surfacePressureMbar),
                            Map.entry("water density", DecoSettings::waterDensity),
                            Map.entry("start CNS", DecoSettings::startCns),
                            Map.entry("end CNS", DecoSettings::endCns),
                            Map.entry("start OTU", DecoSettings::startOtu),
                            Map.entry("end OTU", DecoSettings::endOtu),
                            Map.entry("start tissues", DecoSettings::startTissues),
                            Map.entry("end tissues", DecoSettings::endTissues),
                            Map.entry("firmware", DecoSettings::firmware));

    private static final class Tally {
        private int added;
        private int removed;
        private final Map<Value, Integer> filled = new EnumMap<>(Value.class);
        private final Map<Value, Integer> changed = new EnumMap<>(Value.class);
        private final Map<Value, Integer> dropped = new EnumMap<>(Value.class);
        private final List<String> decoFilled = new ArrayList<>();
        private final List<String> decoChanged = new ArrayList<>();
        private final List<String> decoDropped = new ArrayList<>();
    }

    private ProfileReprocessDiff() {}

    /**
     * @param proposed on the stored profile's clock moved by {@code clockShift} - the content is
     *     compared with the shift taken back out, the shift itself counts as one change
     */
    public static Result compare(
            final List<DiveMeasurement> current,
            final @Nullable DecoSettings currentDeco,
            final List<DiveMeasurement> proposed,
            final @Nullable DecoSettings proposedDeco,
            final Duration clockShift) {
        final var tally = new Tally();
        final var cur = sortedAsStored(current, Duration.ZERO);
        final var pro = sortedAsStored(proposed, clockShift.negated());
        var i = 0;
        var j = 0;
        while (i < cur.size() && j < pro.size()) {
            final var gap = Duration.between(cur.get(i).time(), pro.get(j).time());
            if (gap.abs().compareTo(SAME_SAMPLE) <= 0) {
                compareSample(cur.get(i++), pro.get(j++), tally);
            } else if (gap.isNegative()) {
                tally.added++;
                j++;
            } else {
                tally.removed++;
                i++;
            }
        }
        tally.removed += cur.size() - i;
        tally.added += pro.size() - j;
        compareDeco(currentDeco, proposedDeco, tally);
        return summarize(tally, clockShift);
    }

    private static List<DiveMeasurement> sortedAsStored(
            final List<DiveMeasurement> measurements, final Duration shift) {
        return measurements.stream()
                .map(m -> asStored(m.shifted(shift)))
                .sorted(Comparator.comparing(DiveMeasurement::time))
                .toList();
    }

    /** Through the same conversion the database applies; the gas is compared by mix alone. */
    static DiveMeasurement asStored(final DiveMeasurement m) {
        final var stored = new DiveMeasurementEntity(m, null).toRecord();
        return new DiveMeasurement(
                stored.time(),
                stored.temperature(),
                stored.depth(),
                stored.ndl(),
                stored.deco(),
                m.gas(),
                stored.po2(),
                stored.rmvLiters(),
                stored.n2(),
                stored.o2Tox(),
                stored.cns(),
                stored.mode(),
                stored.timeToSurface());
    }

    private static void compareSample(
            final DiveMeasurement a, final DiveMeasurement b, final Tally t) {
        compare(Value.DEPTH, a.depth(), b.depth(), near(0.005), t);
        compare(
                Value.TEMPERATURE,
                celsius(a.temperature()),
                celsius(b.temperature()),
                near(0.05),
                t);
        compare(Value.NDL, a.ndl(), b.ndl(), Objects::equals, t);
        compare(
                Value.DECO,
                nonEmpty(a.deco()),
                nonEmpty(b.deco()),
                ProfileReprocessDiff::sameStops,
                t);
        compare(Value.GAS, a.gas(), b.gas(), ProfileReprocessDiff::sameMix, t);
        compare(Value.PO2, a.po2(), b.po2(), ProfileReprocessDiff::samePo2, t);
        compare(Value.RMV, a.rmvLiters(), b.rmvLiters(), near(0.01), t);
        compare(Value.N2, a.n2(), b.n2(), near(0.001), t);
        compare(Value.O2_TOX, a.o2Tox(), b.o2Tox(), near(0.01), t);
        compare(Value.CNS, a.cns(), b.cns(), near(0.1), t);
        compare(Value.MODE, a.mode(), b.mode(), Objects::equals, t);
        compare(Value.TTS, a.timeToSurface(), b.timeToSurface(), Objects::equals, t);
    }

    private static <T> void compare(
            final Value value,
            final @Nullable T current,
            final @Nullable T proposed,
            final BiPredicate<T, T> same,
            final Tally t) {
        if (current == null && proposed == null) {
            return;
        }
        if (current == null) {
            t.filled.merge(value, 1, Integer::sum);
        } else if (proposed == null) {
            t.dropped.merge(value, 1, Integer::sum);
        } else if (!same.test(current, proposed)) {
            t.changed.merge(value, 1, Integer::sum);
        }
    }

    private static void compareDeco(
            final @Nullable DecoSettings current,
            final @Nullable DecoSettings proposed,
            final Tally t) {
        for (final var field : DECO_FIELDS) {
            final var a = current == null ? null : field.getValue().apply(current);
            final var b = proposed == null ? null : field.getValue().apply(proposed);
            decoValue(field.getKey(), a, b, t);
        }
        final Map<String, String> a = current == null ? Map.of() : current.details();
        final Map<String, String> b = proposed == null ? Map.of() : proposed.details();
        final var keys = new TreeSet<>(a.keySet());
        keys.addAll(b.keySet());
        keys.forEach(key -> decoValue(key, a.get(key), b.get(key), t));
    }

    private static void decoValue(
            final String name, final @Nullable Object a, final @Nullable Object b, final Tally t) {
        if (a == null && b == null) {
            return;
        }
        if (a == null) {
            t.decoFilled.add(name);
        } else if (b == null) {
            t.decoDropped.add(name);
        } else if (!sameValue(a, b)) {
            t.decoChanged.add(name + " " + a + " → " + b);
        }
    }

    private static Result summarize(final Tally t, final Duration clockShift) {
        final var parts = new ArrayList<String>();
        if (clockShift.abs().compareTo(MIN_CLOCK_CHANGE) >= 0) {
            parts.add("clock moves " + describeShift(clockShift));
        }
        t.changed.forEach((v, n) -> parts.add(v.label + " changes on " + samples(n)));
        t.dropped.forEach((v, n) -> parts.add(v.label + " disappears from " + samples(n)));
        if (t.removed > 0) {
            parts.add(samples(t.removed) + " removed");
        }
        if (!t.decoChanged.isEmpty()) {
            parts.add("deco settings: " + String.join(", ", t.decoChanged));
        }
        if (!t.decoDropped.isEmpty()) {
            parts.add("deco settings lose " + String.join(", ", t.decoDropped));
        }
        final var changed = !parts.isEmpty();
        if (t.added > 0) {
            parts.add(samples(t.added) + " added");
        }
        t.filled.forEach((v, n) -> parts.add(v.label + " added to " + samples(n)));
        if (!t.decoFilled.isEmpty()) {
            parts.add("deco settings gain " + String.join(", ", t.decoFilled));
        }
        if (parts.isEmpty()) {
            return new Result(Outcome.IDENTICAL, "No changes");
        }
        final var summary = String.join("; ", parts);
        return new Result(
                changed ? Outcome.CHANGED : Outcome.ADDITIONS_ONLY,
                Character.toUpperCase(summary.charAt(0)) + summary.substring(1));
    }

    static String describeShift(final Duration shift) {
        final var abs = shift.abs();
        final var parts = new ArrayList<String>();
        if (abs.toHours() > 0) {
            parts.add(abs.toHours() + " h");
        }
        if (abs.toMinutesPart() > 0) {
            parts.add(abs.toMinutesPart() + " min");
        }
        if (abs.toSecondsPart() > 0 && abs.toHours() == 0) {
            parts.add(abs.toSecondsPart() + " s");
        }
        return String.join(" ", parts) + (shift.isNegative() ? " earlier" : " later");
    }

    private static String samples(final int n) {
        return n + (n == 1 ? " sample" : " samples");
    }

    private static BiPredicate<Double, Double> near(final double tolerance) {
        return (a, b) -> Math.abs(a - b) <= tolerance;
    }

    private static @Nullable Double celsius(final @Nullable Temperature temperature) {
        return temperature == null ? null : temperature.celsius();
    }

    private static @Nullable List<DecoStop> nonEmpty(final @Nullable List<DecoStop> stops) {
        return stops == null || stops.isEmpty() ? null : stops;
    }

    private static boolean sameStops(final List<DecoStop> a, final List<DecoStop> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (var k = 0; k < a.size(); k++) {
            final var x = a.get(k);
            final var y = b.get(k);
            if (!Objects.equals(x.type(), y.type())
                    || Math.abs(x.depth() - y.depth()) > 0.01
                    || x.seconds() != y.seconds()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMix(final Gas a, final Gas b) {
        return Math.abs(a.o2() - b.o2()) <= 0.001 && Math.abs(a.he() - b.he()) <= 0.001;
    }

    private static boolean samePo2(final PO2 a, final PO2 b) {
        return sameNullable(a.maxSetPoint(), b.maxSetPoint())
                && sameNullable(a.measured(), b.measured())
                && sameNullable(a.calculated(), b.calculated());
    }

    private static boolean sameNullable(final @Nullable Double a, final @Nullable Double b) {
        return a == null || b == null ? a == b : Math.abs(a - b) <= 0.005;
    }

    private static boolean sameValue(final Object a, final Object b) {
        if (a instanceof final Number x && b instanceof final Number y) {
            return Math.abs(x.doubleValue() - y.doubleValue()) <= 0.001;
        }
        return a.equals(b);
    }
}
