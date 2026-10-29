package ch.sthomas.stddivegastracker.calc.fill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.calc.fill.FillSolver.StageInput;
import ch.sthomas.stddivegastracker.calc.fill.FillSolver.StageResult;
import ch.sthomas.stddivegastracker.calc.fill.FillSolver.Warning;
import ch.sthomas.stddivegastracker.calc.gas.CompressibilityModel;
import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.IdealGas;
import ch.sthomas.stddivegastracker.calc.gas.Units;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

final class FillSolverTest {

    private static final double DEAD_VOLUME_LITRES = 0.05;
    private static final int SEEDS = 100;

    /** Test-only non-ideal model, {@code Z = 1 + (0.0002 + 0.0006·he)·pAbs}. */
    private static final class LinearTestModel implements CompressibilityModel {
        @Override
        public double z(final double pAbsBar, final GasMix mix) {
            return 1 + (0.0002 + 0.0006 * mix.he()) * pAbsBar;
        }
    }

    /** Test-only model whose Z collapses with the N2 share, so the 2·content bound is too small. */
    private static final class MixSensitiveTestModel implements CompressibilityModel {
        @Override
        public double z(final double pAbsBar, final GasMix mix) {
            return 1 / (1 + 100 * mix.n2());
        }
    }

    private static final List<CompressibilityModel> MODELS =
            List.of(IdealGas.INSTANCE, new LinearTestModel(), VirialFit.INSTANCE);

    // --- DOMAIN §11.1 golden values ---

    private static FillSolver.Input goldenInput(final @Nullable GasMix endAnalysis) {
        return new FillSolver.Input(
                12,
                0,
                GasMix.AIR,
                List.of(
                        new StageInput(GasMix.HELIUM, 70, true),
                        new StageInput(GasMix.OXYGEN, 92, true),
                        new StageInput(GasMix.AIR, 200, false)),
                endAnalysis);
    }

    @Test
    void goldenFillMatchesTheWorkedExample() {
        final var result =
                FillSolver.solve(goldenInput(null), IdealGas.INSTANCE, DEAD_VOLUME_LITRES);

        final var he = result.stages().get(0);
        final var o2 = result.stages().get(1);
        final var air = result.stages().get(2);
        assertThat(he.addedLitres()).isCloseTo(829.0155, within(1e-4));
        assertThat(o2.addedLitres()).isCloseTo(260.5477, within(1e-4));
        assertThat(air.addedLitres()).isCloseTo(1279.0526, within(1e-4));
        assertThat(he.lineLossLitres()).isCloseTo(3.4542, within(1e-4));
        assertThat(o2.lineLossLitres()).isCloseTo(4.5398, within(1e-4));
        assertThat(air.lineLossLitres()).isZero();
        assertThat(he.drawLitres()).isCloseTo(832.4697, within(1e-4));
        assertThat(o2.drawLitres()).isCloseTo(265.0875, within(1e-4));
        assertThat(air.drawLitres()).isZero();
        assertThat(he.addedHeLitres()).isCloseTo(829.0155, within(1e-4));
        assertThat(he.addedO2Litres()).isZero();
        assertThat(o2.addedO2Litres()).isCloseTo(260.5477, within(1e-4));
        assertThat(o2.addedHeLitres()).isZero();
        assertThat(air.addedO2Litres()).isCloseTo(1279.0526 * 0.209, within(1e-4));
        assertThat(air.addedHeLitres()).isZero();
        assertThat(result.endMix().o2()).isCloseTo(0.222790, within(1e-6));
        assertThat(result.endMix().he()).isCloseTo(0.348236, within(1e-6));
        assertThat(result.endPressureBar()).isEqualTo(200);
        assertThat(result.warnings()).isEmpty();
    }

    // --- bisection against the IdealGas closed form (DOMAIN §5.2) ---

    @Test
    void bisectionEqualsTheIdealClosedForm() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            final var input = randomInput(new Random(seed));
            final var result = FillSolver.solve(input, IdealGas.INSTANCE, DEAD_VOLUME_LITRES);

            double previousBar = input.startPressureBar();
            for (int k = 0; k < input.stages().size(); k++) {
                final double pressureBar = input.stages().get(k).pressureAfterBar();
                final double closedForm =
                        input.waterVolumeLitres() * (pressureBar - previousBar) / Units.P_ATM;
                assertThat(result.stages().get(k).addedLitres())
                        .as("seed %d, stage %d", seed, k + 1)
                        .isCloseTo(closedForm, within(1e-6));
                previousBar = pressureBar;
            }
        }
    }

    @Test
    void bracketIsWidenedWhenTwiceTheSourceContentIsTooSmall() {
        final var input =
                new FillSolver.Input(
                        12, 0, GasMix.AIR, List.of(new StageInput(GasMix.HELIUM, 10, true)), null);
        final var model = new MixSensitiveTestModel();

        final var result = FillSolver.solve(input, model, DEAD_VOLUME_LITRES);

        final double addedLitres = result.stages().getFirst().addedLitres();
        assertThat(addedLitres)
                .isGreaterThan(2 * GasContent.contentLitres(12, 10, GasMix.HELIUM, model));
        final double startLitres = GasContent.contentLitres(12, 0, GasMix.AIR, model);
        assertThat(GasContent.contentLitres(12, 10, result.endMix(), model))
                .isCloseTo(startLitres + addedLitres, within(1e-5));
    }

    @Test
    void unsolvableFillThrows() {
        // Z is NaN for every intermediate mix, so no bracket ever has a sign change
        // checkstyle: explicit-type lambda target
        final CompressibilityModel model =
                (_, mix) ->
                        mix.equals(GasMix.AIR) ? 1.0 : mix.equals(GasMix.HELIUM) ? 1e6 : Double.NaN;
        final var input =
                new FillSolver.Input(
                        12, 0, GasMix.AIR, List.of(new StageInput(GasMix.HELIUM, 200, true)), null);

        assertThatThrownBy(() -> FillSolver.solve(input, model, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- model-independent properties, every model (DOMAIN §5.2, §5.4, §5.5) ---

    private record Case(
            String context,
            FillSolver.Input input,
            CompressibilityModel model,
            double deadVolumeLitres,
            FillSolver.Result result,
            double startLitres,
            double addedLitres) {}

    private static void forEveryModelAndSeed(final Consumer<Case> check) {
        for (final var model : MODELS) {
            for (long seed = 1; seed <= SEEDS; seed++) {
                final var random = new Random(seed);
                final var input = randomInput(random);
                final double deadVolumeLitres = random.nextBoolean() ? 0 : random.nextDouble();
                final var result = FillSolver.solve(input, model, deadVolumeLitres);
                final double startLitres =
                        GasContent.contentLitres(
                                input.waterVolumeLitres(),
                                input.startPressureBar(),
                                input.startMix(),
                                model);
                final double addedLitres =
                        result.stages().stream().mapToDouble(StageResult::addedLitres).sum();
                check.accept(
                        new Case(
                                "%s, seed %d".formatted(model.getClass().getSimpleName(), seed),
                                input,
                                model,
                                deadVolumeLitres,
                                result,
                                startLitres,
                                addedLitres));
            }
        }
    }

    @Test
    void addedLitresEqualContentGained() {
        forEveryModelAndSeed(
                c -> {
                    final double endLitres =
                            GasContent.contentLitres(
                                    c.input().waterVolumeLitres(),
                                    c.result().endPressureBar(),
                                    c.result().endMix(),
                                    c.model());
                    assertThat(c.addedLitres())
                            .as(c.context())
                            .isCloseTo(endLitres - c.startLitres(), within(1e-6));
                });
    }

    @Test
    void endPressureReproducesTheComputedEndContent() {
        forEveryModelAndSeed(
                c ->
                        assertThat(
                                        GasContent.pressureFor(
                                                c.input().waterVolumeLitres(),
                                                c.startLitres() + c.addedLitres(),
                                                c.result().endMix(),
                                                c.model()))
                                .as(c.context())
                                .isCloseTo(c.result().endPressureBar(), within(1e-5)));
    }

    @Test
    void componentLitresAddUpToTheEndMix() {
        forEveryModelAndSeed(
                c -> {
                    double o2Litres = c.startLitres() * c.input().startMix().o2();
                    double heLitres = c.startLitres() * c.input().startMix().he();
                    for (int k = 0; k < c.input().stages().size(); k++) {
                        final var source = c.input().stages().get(k).sourceMix();
                        final var stage = c.result().stages().get(k);
                        assertThat(stage.addedLitres()).as(c.context()).isNotNegative();
                        assertThat(stage.addedO2Litres())
                                .as(c.context())
                                .isCloseTo(stage.addedLitres() * source.o2(), within(1e-9));
                        assertThat(stage.addedHeLitres())
                                .as(c.context())
                                .isCloseTo(stage.addedLitres() * source.he(), within(1e-9));
                        o2Litres += stage.addedO2Litres();
                        heLitres += stage.addedHeLitres();
                    }
                    final double totalLitres = c.startLitres() + c.addedLitres();
                    assertThat(c.result().endMix().o2())
                            .as(c.context())
                            .isCloseTo(o2Litres / totalLitres, within(1e-9));
                    assertThat(c.result().endMix().he())
                            .as(c.context())
                            .isCloseTo(heLitres / totalLitres, within(1e-9));
                });
    }

    @Test
    void drawIsAddedPlusLineLossForVesselStagesAndZeroForAir() {
        forEveryModelAndSeed(
                c -> {
                    for (int k = 0; k < c.input().stages().size(); k++) {
                        final var in = c.input().stages().get(k);
                        final var stage = c.result().stages().get(k);
                        if (in.fromVessel()) {
                            final double lineLossLitres =
                                    c.deadVolumeLitres() * in.pressureAfterBar() / Units.P_ATM;
                            assertThat(stage.lineLossLitres())
                                    .as(c.context())
                                    .isCloseTo(lineLossLitres, within(1e-12));
                            assertThat(stage.drawLitres())
                                    .as(c.context())
                                    .isCloseTo(
                                            stage.addedLitres() + stage.lineLossLitres(),
                                            within(1e-12));
                        } else {
                            assertThat(stage.lineLossLitres()).as(c.context()).isZero();
                            assertThat(stage.drawLitres()).as(c.context()).isZero();
                        }
                    }
                });
    }

    private static FillSolver.Input randomInput(final Random random) {
        final double startPressureBar = random.nextBoolean() ? 0 : random.nextDouble() * 50;
        final var stages = new ArrayList<StageInput>();
        double pressureBar = startPressureBar;
        for (int k = 1 + random.nextInt(5); k > 0; k--) {
            pressureBar += 1 + random.nextDouble() * 50;
            stages.add(new StageInput(randomMix(random), pressureBar, random.nextBoolean()));
        }
        return new FillSolver.Input(
                1 + random.nextDouble() * 49, startPressureBar, randomMix(random), stages, null);
    }

    private static GasMix randomMix(final Random random) {
        final double o2 = random.nextDouble();
        return new GasMix(o2, random.nextDouble() * (1 - o2));
    }

    // --- validation (messages are user-facing) ---

    @Test
    void rejectsAFillWithoutStages() {
        assertThatThrownBy(() -> new FillSolver.Input(12, 0, GasMix.AIR, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A fill needs at least one stage");
    }

    @Test
    void rejectsAFirstStageThatIsNotAboveTheStartPressure() {
        for (final double firstBar : new double[] {40, 30}) {
            assertThatThrownBy(
                            () ->
                                    new FillSolver.Input(
                                            12,
                                            40,
                                            GasMix.AIR,
                                            List.of(new StageInput(GasMix.HELIUM, firstBar, true)),
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stage 1 pressure must be above the start pressure");
        }
    }

    @Test
    void rejectsStagePressuresThatDoNotIncrease() {
        for (final double secondBar : new double[] {70, 60}) {
            assertThatThrownBy(
                            () ->
                                    new FillSolver.Input(
                                            12,
                                            0,
                                            GasMix.AIR,
                                            List.of(
                                                    new StageInput(GasMix.HELIUM, 70, true),
                                                    new StageInput(GasMix.OXYGEN, secondBar, true)),
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stage 2 pressure must be above stage 1");
        }
    }

    @Test
    void rejectsAnInvalidLineDeadVolume() {
        for (final double deadVolumeLitres :
                new double[] {-0.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(
                            () ->
                                    FillSolver.solve(
                                            goldenInput(null), IdealGas.INSTANCE, deadVolumeLitres))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Line dead volume must be a number of at least 0 litres");
        }
    }

    @Test
    void waterVolumeAndPressureChecksComeFromGasContent() {
        final var noVolume =
                new FillSolver.Input(
                        0, 0, GasMix.AIR, List.of(new StageInput(GasMix.HELIUM, 70, true)), null);
        final var negativeStart =
                new FillSolver.Input(
                        12, -1, GasMix.AIR, List.of(new StageInput(GasMix.HELIUM, 70, true)), null);

        assertThatThrownBy(() -> FillSolver.solve(noVolume, IdealGas.INSTANCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Water volume must be a number greater than 0");
        assertThatThrownBy(() -> FillSolver.solve(negativeStart, IdealGas.INSTANCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pressure must be a number of at least 0 bar");
    }

    // --- ANALYSIS_MISMATCH (DOMAIN §5.2) ---

    @Test
    void noWarningWhenTheAnalysisMatches() {
        // golden end mix 0.222790 / 0.348236; 0.005 off is within the 0.01 tolerance (absent:
        // golden test)
        for (final var analysis :
                new GasMix[] {new GasMix(0.2228, 0.3482), new GasMix(0.2278, 0.3432)}) {
            assertThat(FillSolver.solve(goldenInput(analysis), IdealGas.INSTANCE, 0).warnings())
                    .isEmpty();
        }
    }

    @Test
    void warnsWhenO2OrHeDeviatesByMoreThanOnePercentagePoint() {
        for (final var analysis :
                new GasMix[] {new GasMix(0.2428, 0.3482), new GasMix(0.2228, 0.3282)}) {
            final var result = FillSolver.solve(goldenInput(analysis), IdealGas.INSTANCE, 0);

            assertThat(result.warnings()).containsExactly(Warning.ANALYSIS_MISMATCH);
            assertThat(result.endMix().o2()).isCloseTo(0.222790, within(1e-6));
        }
    }

    @Test
    void theMismatchCheckIsPublicSoStoredFillsCanBeRecheckedOnRead() {
        final var end = new GasMix(0.2228, 0.3482);

        assertThat(FillSolver.analysisMismatch(end, null)).isFalse();
        assertThat(FillSolver.analysisMismatch(end, end)).isFalse();
        assertThat(FillSolver.analysisMismatch(end, new GasMix(0.2278, 0.3432))).isFalse();
        assertThat(FillSolver.analysisMismatch(end, new GasMix(0.2429, 0.3482))).isTrue();
        assertThat(FillSolver.analysisMismatch(end, new GasMix(0.2228, 0.3381))).isTrue();
    }
}
