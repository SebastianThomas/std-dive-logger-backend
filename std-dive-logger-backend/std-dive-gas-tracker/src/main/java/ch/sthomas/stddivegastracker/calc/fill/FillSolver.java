package ch.sthomas.stddivegastracker.calc.fill;

import ch.sthomas.stddivegastracker.calc.gas.CompressibilityModel;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.Units;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Computes a fill from stage pressures, stage by stage (DOMAIN §5.2, §5.4, §5.5). */
public final class FillSolver {

    private static final double TOLERANCE_LITRES = 1e-6;
    private static final int MAX_ITERATIONS = 200;
    private static final int MAX_DOUBLINGS = 64;
    private static final double ANALYSIS_TOLERANCE_FRACTION = 0.01;

    private FillSolver() {}

    /**
     * Fill inputs. Stage pressures must strictly increase, the first above the start pressure
     * (DOMAIN §5.2). Water volume and pressures are checked by {@link GasContent} when solving.
     */
    public record Input(
            double waterVolumeLitres,
            double startPressureBar,
            GasMix startMix,
            List<StageInput> stages,
            @Nullable GasMix endAnalysis) {

        public Input {
            stages = List.copyOf(stages);
            if (stages.isEmpty()) {
                throw new IllegalArgumentException("A fill needs at least one stage");
            }
            double previousBar = startPressureBar;
            for (int i = 0; i < stages.size(); i++) {
                final double pressureBar = stages.get(i).pressureAfterBar();
                if (!(pressureBar > previousBar)) {
                    throw new IllegalArgumentException(
                            i == 0
                                    ? "Stage 1 pressure must be above the start pressure"
                                    : "Stage " + (i + 1) + " pressure must be above stage " + i);
                }
                previousBar = pressureBar;
            }
        }
    }

    /** One stage: the gauge after it and the gas added. AIR is {@code fromVessel == false}. */
    public record StageInput(GasMix sourceMix, double pressureAfterBar, boolean fromVessel) {}

    /** Per-stage results, DOMAIN §5.4; {@code drawLitres} is 0 for AIR (no draw, no line loss). */
    public record StageResult(
            double addedLitres,
            double addedO2Litres,
            double addedHeLitres,
            double lineLossLitres,
            double drawLitres) {}

    public record Result(
            List<StageResult> stages,
            double endPressureBar,
            GasMix endMix,
            List<Warning> warnings) {

        public Result {
            stages = List.copyOf(stages);
            warnings = List.copyOf(warnings);
        }
    }

    public enum Warning {
        ANALYSIS_MISMATCH
    }

    public static Result solve(
            final Input input,
            final CompressibilityModel model,
            final double lineDeadVolumeLitres) {
        if (!Double.isFinite(lineDeadVolumeLitres) || lineDeadVolumeLitres < 0) {
            throw new IllegalArgumentException(
                    "Line dead volume must be a number of at least 0 litres");
        }
        final double waterVolumeLitres = input.waterVolumeLitres();
        double litres =
                GasContent.contentLitres(
                        waterVolumeLitres, input.startPressureBar(), input.startMix(), model);
        double o2Litres = litres * input.startMix().o2();
        double heLitres = litres * input.startMix().he();

        final var results = new ArrayList<StageResult>();
        for (final var stage : input.stages()) {
            final double beforeLitres = litres;
            final double beforeO2Litres = o2Litres;
            final double beforeHeLitres = heLitres;
            final var source = stage.sourceMix();
            final double pressureBar = stage.pressureAfterBar();
            // content(p_k, mix(a)) - (L_{k-1} + a); the stage amount is its root (DOMAIN §5.2)
            // checkstyle: explicit-type lambda target
            final DoubleUnaryOperator surplusLitres =
                    candidateLitres -> {
                        final double totalLitres = beforeLitres + candidateLitres;
                        final var mix =
                                new GasMix(
                                        (beforeO2Litres + candidateLitres * source.o2())
                                                / totalLitres,
                                        (beforeHeLitres + candidateLitres * source.he())
                                                / totalLitres);
                        return GasContent.contentLitres(waterVolumeLitres, pressureBar, mix, model)
                                - totalLitres;
                    };
            final double addedLitres =
                    findRoot(
                            surplusLitres,
                            2
                                    * GasContent.contentLitres(
                                            waterVolumeLitres, pressureBar, source, model));
            final double lineLossLitres =
                    stage.fromVessel() ? lineDeadVolumeLitres * pressureBar / Units.P_ATM : 0.0;
            results.add(
                    new StageResult(
                            addedLitres,
                            addedLitres * source.o2(),
                            addedLitres * source.he(),
                            lineLossLitres,
                            stage.fromVessel() ? addedLitres + lineLossLitres : 0.0));
            litres += addedLitres;
            o2Litres += addedLitres * source.o2();
            heLitres += addedLitres * source.he();
        }

        final var endMix = new GasMix(o2Litres / litres, heLitres / litres);
        return new Result(
                results,
                input.stages().getLast().pressureAfterBar(),
                endMix,
                analysisMismatch(endMix, input.endAnalysis())
                        ? List.of(Warning.ANALYSIS_MISMATCH)
                        : List.of());
    }

    /** DOMAIN §5.2: the analysed O2 or He differs from the computed end mix by more than 0.01. */
    public static boolean analysisMismatch(final GasMix endMix, final @Nullable GasMix analysis) {
        return analysis != null
                && (Math.abs(analysis.o2() - endMix.o2()) > ANALYSIS_TOLERANCE_FRACTION
                        || Math.abs(analysis.he() - endMix.he()) > ANALYSIS_TOLERANCE_FRACTION);
    }

    /**
     * Bisection for the root of a decreasing function on [0, upper]. The spec's {@code 2·content}
     * bound is widened until the sign changes; non-finite values hit the cap (DOMAIN §5.2).
     */
    private static double findRoot(final DoubleUnaryOperator f, final double initialUpperLitres) {
        double highLitres = initialUpperLitres;
        for (int doublings = 0; !(f.applyAsDouble(highLitres) <= 0); doublings++) {
            if (doublings == MAX_DOUBLINGS) {
                throw new IllegalStateException("The fill could not be calculated");
            }
            highLitres *= 2;
        }
        double lowLitres = 0.0;
        for (int i = 0; i < MAX_ITERATIONS && highLitres - lowLitres > TOLERANCE_LITRES; i++) {
            final double midLitres = (lowLitres + highLitres) / 2;
            if (f.applyAsDouble(midLitres) > 0) {
                lowLitres = midLitres;
            } else {
                highLitres = midLitres;
            }
        }
        return (lowLitres + highLitres) / 2;
    }
}
