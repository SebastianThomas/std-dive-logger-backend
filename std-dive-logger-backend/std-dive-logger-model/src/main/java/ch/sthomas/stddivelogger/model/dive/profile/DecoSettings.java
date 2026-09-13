package ch.sthomas.stddivelogger.model.dive.profile;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * How a dive computer computed a profile's decompression and oxygen-exposure figures - which
 * algorithm (and whose implementation of it), with which gradient factors / conservatism, against
 * which surface pressure and water density, and the CNS / OTU / tissue loading the device itself
 * reported. Kept per profile so the same dive's computers (and their algorithms) can be compared
 * later.
 *
 * <p>The typed fields are the ones comparable across devices; {@code details} keeps everything else
 * the source file reports, keyed by the source's own name, so nothing the file carries is lost.
 *
 * @param algorithm normalized name, e.g. "Bühlmann ZHL-16C", "VPM-B" (see {@link #normalize})
 * @param implementation whose implementation computed the figures - the device's maker
 * @param gfLow gradient factor low, percent
 * @param gfHigh gradient factor high, percent
 * @param conservatism the device's own conservatism setting, as it labels it (e.g. "+3" on VPM-B)
 * @param surfacePressureMbar surface pressure the device assumed / measured
 * @param waterDensity water density the device assumed, kg/m³ (1000 fresh, ~1025 salt)
 * @param startCns device-reported CNS at the start of the dive, percent
 * @param endCns device-reported CNS at the end of the dive, percent
 * @param startOtu device-reported OTUs at the start of the dive
 * @param endOtu device-reported OTUs at the end of the dive
 * @param startTissues device-reported tissue loading before the dive, in the device's own units
 * @param endTissues device-reported tissue loading after the dive, in the device's own units
 * @param firmware the device's firmware / software version
 * @param details everything else the source reports about these calculations, raw
 */
public record DecoSettings(
        @Nullable String algorithm,
        @Nullable String implementation,
        @Nullable Integer gfLow,
        @Nullable Integer gfHigh,
        @Nullable String conservatism,
        @Nullable Double surfacePressureMbar,
        @Nullable Double waterDensity,
        @Nullable Double startCns,
        @Nullable Double endCns,
        @Nullable Double startOtu,
        @Nullable Double endOtu,
        @Nullable TissueLoading startTissues,
        @Nullable TissueLoading endTissues,
        @Nullable String firmware,
        Map<String, String> details) {

    /** Per-compartment inert-gas loading, compartment 1 first. */
    public record TissueLoading(List<Double> nitrogen, List<Double> helium) {
        public TissueLoading {
            nitrogen = nitrogen == null ? List.of() : List.copyOf(nitrogen);
            helium = helium == null ? List.of() : List.copyOf(helium);
        }
    }

    public DecoSettings {
        // Sorted, so equal settings serialize (and compare) identically whatever the source order.
        details = details == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(details));
    }

    /** Collects a source's raw settings for {@code details}, skipping absent / blank values. */
    public static final class Details {
        private final Map<String, String> values = new TreeMap<>();

        public Details put(final String key, final @Nullable Object value) {
            if (value != null && !String.valueOf(value).isBlank()) {
                values.put(key, String.valueOf(value));
            }
            return this;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public Map<String, String> build() {
            return values;
        }
    }

    /** Settings with only {@code details} set - the start point readers add typed fields to. */
    public static DecoSettings ofDetails(final Map<String, String> details) {
        return new DecoSettings(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                details);
    }

    /**
     * The usual name of a decompression algorithm as sources spell it: UDDF ids ("zhl16c"), FIT
     * enums ("ZHL_16C"), Suunto ("Buhlmann", which on its computers is the ZHL-16C with gradient
     * factors), Shearwater / Subsurface ("GF", "VPM-B", "VPM-B/GFS", "DCIEM"). Anything else is
     * kept as the source wrote it.
     */
    public static @Nullable String normalize(final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final var key = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        if (key.equals("zhl16c")
                || key.equals("buhlmann")
                || key.equals("bühlmann")
                || key.equals("buehlmann")
                || key.equals("gf")
                || key.equals("buhlmannzhl16c")
                || key.equals("bühlmannzhl16c")
                || key.equals("buehlmannzhl16c")) {
            return "Bühlmann ZHL-16C";
        }
        if (key.equals("vpmb") || key.equals("vpm")) {
            return "VPM-B";
        }
        if (key.equals("vpmb/gfs")) {
            return "VPM-B/GFS";
        }
        if (key.equals("dciem")) {
            return "DCIEM";
        }
        return raw.trim();
    }

    /**
     * Combines what two sources know about the same profile - e.g. its UDDF and its native XML
     * export. Where both carry a value the richer source wins, so the result doesn't depend on
     * which of the two came first; {@code details} is the union of both.
     */
    public static @Nullable DecoSettings merge(
            final @Nullable DecoSettings a, final @Nullable DecoSettings b) {
        if (a == null || b == null) {
            return a != null ? a : b;
        }
        final var aWins =
                a.filledFields() != b.filledFields()
                        ? a.filledFields() > b.filledFields()
                        : a.details.size() != b.details.size()
                                ? a.details.size() > b.details.size()
                                : a.toString().compareTo(b.toString()) >= 0;
        final var p = aWins ? a : b;
        final var s = aWins ? b : a;
        final var details = new TreeMap<>(s.details);
        details.putAll(p.details);
        return new DecoSettings(
                first(p.algorithm, s.algorithm),
                first(p.implementation, s.implementation),
                first(p.gfLow, s.gfLow),
                first(p.gfHigh, s.gfHigh),
                first(p.conservatism, s.conservatism),
                first(p.surfacePressureMbar, s.surfacePressureMbar),
                first(p.waterDensity, s.waterDensity),
                first(p.startCns, s.startCns),
                first(p.endCns, s.endCns),
                first(p.startOtu, s.startOtu),
                first(p.endOtu, s.endOtu),
                first(p.startTissues, s.startTissues),
                first(p.endTissues, s.endTissues),
                first(p.firmware, s.firmware),
                details);
    }

    private long filledFields() {
        return Stream.of(
                        algorithm,
                        implementation,
                        gfLow,
                        gfHigh,
                        conservatism,
                        surfacePressureMbar,
                        waterDensity,
                        startCns,
                        endCns,
                        startOtu,
                        endOtu,
                        startTissues,
                        endTissues,
                        firmware)
                .filter(Objects::nonNull)
                .count();
    }

    private static <T> @Nullable T first(final @Nullable T preferred, final @Nullable T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
