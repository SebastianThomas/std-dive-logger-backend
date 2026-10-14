package ch.sthomas.stddivelogger.service.processing;

import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * Finds where to move a target profile so its depth curve lies on a reference's.
 *
 * <ul>
 *   <li>A recording that starts / ends at the surface counts as 0 m beyond that edge (a computer
 *       logs while submerged), so underwater time one has and the other doesn't is a mismatch -
 *       comparing only the overlap let two surface tails "match" perfectly. An edge cut off at
 *       depth (a trim) says nothing about what came before / after it.
 *   <li>Every offset overlapping at least half the shorter profile is tried (coarse grid, then
 *       refined), not a fixed window around the reference start - a short profile can belong
 *       anywhere in a long one.
 * </ul>
 */
public class ProfileAlignService {

    private static final Duration STEP = Duration.ofSeconds(1);
    // Coarse pass resolution: at most this many samples on the longer profile.
    private static final int COARSE_SAMPLES = 2000;
    private static final double SUBMERGED_DEPTH = 1.0;
    // Computers start / stop logging around 1-1.5 m.
    private static final double SURFACE_EDGE_DEPTH = 2.0;

    private ProfileAlignService() {}

    /** The target profile's new start. */
    public static Instant alignProfilesAuto(
            final DiveProfile referenceProfile,
            final DiveProfile targetProfile,
            final AlignType type) {
        if (type == AlignType.MANUAL) {
            throw new IllegalArgumentException("MANUAL alignment needs an explicit start time.");
        }
        final var reference = samples(referenceProfile);
        final var target = samples(targetProfile);
        if (!reference.submerged() || !target.submerged()) {
            throw new IllegalArgumentException(
                    "Both profiles need samples deeper than "
                            + SUBMERGED_DEPTH
                            + " m to be aligned automatically.");
        }
        final var stepMs = STEP.toMillis();
        final var r = reference.resample(stepMs);
        final var g = target.resample(stepMs);
        // Offsets in steps: target sample j sits on reference sample j + k.
        final var currentK = Math.round((double) (target.firstMs() - reference.firstMs()) / stepMs);
        final var factor = Math.max(1, Math.max(r.length(), g.length()) / COARSE_SAMPLES);
        final int bestK;
        if (factor == 1) {
            bestK = bestOffset(r, g, -(g.length() - 1), r.length() - 1, currentK, type);
        } else {
            final var coarseR = reference.resample(stepMs * factor);
            final var coarseG = target.resample(stepMs * factor);
            final var coarseK =
                    bestOffset(
                            coarseR,
                            coarseG,
                            -(coarseG.length() - 1),
                            coarseR.length() - 1,
                            Math.round((double) currentK / factor),
                            type);
            bestK =
                    bestOffset(
                            r,
                            g,
                            coarseK * factor - 2 * factor,
                            coarseK * factor + 2 * factor,
                            currentK,
                            type);
        }
        final var shiftMs = reference.firstMs() + bestK * stepMs - target.firstMs();
        return targetProfile.start().plusMillis(shiftMs);
    }

    /**
     * The cheapest offset in {@code [fromK, toK]}. Ties go to the lower average distance (a min-max
     * cost is flat wherever an unmatched dive sets the maximum), then to the one closest to {@code
     * currentK}.
     */
    private static int bestOffset(
            final Curve r,
            final Curve g,
            final int fromK,
            final int toK,
            final long currentK,
            final AlignType type) {
        var bestK = fromK;
        var bestCost = Double.POSITIVE_INFINITY;
        var bestTieBreak = Double.POSITIVE_INFINITY;
        for (var k = fromK; k <= toK; k++) {
            final var cost = cost(r, g, k, type);
            if (cost > bestCost || cost == Double.POSITIVE_INFINITY) {
                continue;
            }
            final var tieBreak =
                    type == AlignType.AUTO_MIN_AVG_DISTANCE
                            ? cost
                            : cost(r, g, k, AlignType.AUTO_MIN_AVG_DISTANCE);
            if (cost < bestCost
                    || tieBreak < bestTieBreak
                    || (tieBreak == bestTieBreak
                            && Math.abs(k - currentK) < Math.abs(bestK - currentK))) {
                bestCost = cost;
                bestTieBreak = tieBreak;
                bestK = k;
            }
        }
        if (bestCost == Double.POSITIVE_INFINITY) {
            throw new IllegalStateException("Could not align profiles, no valid overlap range");
        }
        return bestK;
    }

    /**
     * Distance between the curves with the target moved by {@code k} samples: over their overlap,
     * plus wherever one records and the other's surface edge stands for 0 m. Infinite below the
     * minimum overlap.
     */
    static double cost(final Curve r, final Curve g, final int k, final AlignType type) {
        final var nr = r.length();
        final var ng = g.length();
        // Overlap: reference [lo, hi), target [lo - k, hi - k).
        final var lo = Math.clamp(k, 0, nr);
        final var hi = Math.clamp(ng + k, lo, nr);
        if (hi - lo < Math.max(1, Math.min(nr, ng) / 2)) {
            return Double.POSITIVE_INFINITY;
        }
        final var max = type == AlignType.AUTO_MIN_MAX_DISTANCE;
        final var square = type == AlignType.AUTO_MIN_AVG_SQ_DISTANCE;
        var acc = 0.0;
        for (var i = lo; i < hi; i++) {
            final var d = Math.abs(r.depths()[i] - g.depths()[i - k]);
            acc = max ? Math.max(acc, d) : acc + (square ? d * d : d);
        }
        long count = hi - lo;
        // Reference before / after the target's recording, then the other way round.
        if (g.surfaceStart()) {
            acc = combine(acc, r.prefix(lo), type);
            count += lo;
        }
        if (g.surfaceEnd()) {
            acc = combine(acc, r.suffix(hi), type);
            count += nr - hi;
        }
        if (r.surfaceStart()) {
            acc = combine(acc, g.prefix(lo - k), type);
            count += lo - k;
        }
        if (r.surfaceEnd()) {
            acc = combine(acc, g.suffix(hi - k), type);
            count += ng - (hi - k);
        }
        return max ? acc : acc / count;
    }

    /** Adds the depths of a part compared against 0 m: {sum, sum of squares, max}. */
    private static double combine(final double acc, final double[] part, final AlignType type) {
        return switch (type) {
            case AUTO_MIN_MAX_DISTANCE -> Math.max(acc, part[2]);
            case AUTO_MIN_AVG_SQ_DISTANCE -> acc + part[1];
            default -> acc + part[0];
        };
    }

    private static Samples samples(final DiveProfile profile) {
        final var measurements = profile.measurements();
        if (measurements == null) {
            throw new IllegalArgumentException("Profile " + profile.id() + " has no samples.");
        }
        // Finite depths at strictly increasing times - repeated timestamps would divide by zero.
        final var times = new long[measurements.size()];
        final var depths = new double[measurements.size()];
        var n = 0;
        for (final var m : measurements.stream().map(DiveMeasurementWithId::measurement).toList()) {
            final var t = m.time().toEpochMilli();
            if (Double.isFinite(m.depth()) && (n == 0 || t > times[n - 1])) {
                times[n] = t;
                depths[n++] = Math.max(0.0, m.depth());
            }
        }
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Profile " + profile.id() + " has too few samples to align.");
        }
        return new Samples(Arrays.copyOf(times, n), Arrays.copyOf(depths, n));
    }

    private record Samples(long[] times, double[] depths) {
        long firstMs() {
            return times[0];
        }

        boolean submerged() {
            return Arrays.stream(depths).anyMatch(d -> d > SUBMERGED_DEPTH);
        }

        /** Linearly interpolated depths every {@code stepMs} from the first sample on. */
        Curve resample(final long stepMs) {
            final var span = times[times.length - 1] - times[0];
            final var out = new double[Math.toIntExact(span / stepMs) + 1];
            var j = 0;
            for (var i = 0; i < out.length; i++) {
                final var t = times[0] + i * stepMs;
                while (j < times.length - 2 && times[j + 1] <= t) {
                    j++;
                }
                final var frac =
                        Math.clamp((double) (t - times[j]) / (times[j + 1] - times[j]), 0.0, 1.0);
                out[i] = depths[j] + (depths[j + 1] - depths[j]) * frac;
            }
            return new Curve(
                    out,
                    depths[0] <= SURFACE_EDGE_DEPTH,
                    depths[depths.length - 1] <= SURFACE_EDGE_DEPTH);
        }
    }

    /** Evenly spaced depths, with running sums / maxima for the parts outside an overlap. */
    static final class Curve {
        private final double[] depths;
        private final boolean surfaceStart;
        private final boolean surfaceEnd;
        private final double[] sum;
        private final double[] sumSq;
        private final double[] prefixMax;
        private final double[] suffixMax;

        Curve(final double[] depths, final boolean surfaceStart, final boolean surfaceEnd) {
            this.depths = depths;
            this.surfaceStart = surfaceStart;
            this.surfaceEnd = surfaceEnd;
            final var n = depths.length;
            sum = new double[n + 1];
            sumSq = new double[n + 1];
            prefixMax = new double[n + 1];
            suffixMax = new double[n + 1];
            for (var i = 0; i < n; i++) {
                sum[i + 1] = sum[i] + depths[i];
                sumSq[i + 1] = sumSq[i] + depths[i] * depths[i];
                prefixMax[i + 1] = Math.max(prefixMax[i], depths[i]);
            }
            for (var i = n - 1; i >= 0; i--) {
                suffixMax[i] = Math.max(suffixMax[i + 1], depths[i]);
            }
        }

        double[] depths() {
            return depths;
        }

        int length() {
            return depths.length;
        }

        boolean surfaceStart() {
            return surfaceStart;
        }

        boolean surfaceEnd() {
            return surfaceEnd;
        }

        /** {sum, sum of squares, max} of the first {@code end} depths. */
        double[] prefix(final int end) {
            return new double[] {sum[end], sumSq[end], prefixMax[end]};
        }

        /** The same from {@code start} on. */
        double[] suffix(final int start) {
            final var n = depths.length;
            return new double[] {sum[n] - sum[start], sumSq[n] - sumSq[start], suffixMax[start]};
        }
    }
}
