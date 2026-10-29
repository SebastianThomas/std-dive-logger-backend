package ch.sthomas.stddivegastracker.calc.gas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

final class GasContentTest {

    private static final double VW = 12.0;
    private static final GasMix TRIMIX_21_35 = new GasMix(0.21, 0.35);

    /** Test-only non-ideal model, proven against the contract in LinearTestModelContractTest. */
    private static final CompressibilityModel LINEAR = LinearTestModelContractTest.MODEL;

    private static final List<CompressibilityModel> MODELS = List.of(IdealGas.INSTANCE, LINEAR);
    private static final List<GasMix> MIXES =
            List.of(GasMix.AIR, GasMix.OXYGEN, GasMix.HELIUM, TRIMIX_21_35);

    // --- DOMAIN §11.1 golden contents (Vw = 12 L, IdealGas) ---

    @Test
    void contentMatchesTheWorkedExample() {
        assertContent(0, 12.0000);
        assertContent(70, 841.0155);
        assertContent(92, 1101.5633);
        assertContent(200, 2380.6158);
    }

    private static void assertContent(final double pressureBarGauge, final double expected) {
        final double actual =
                GasContent.contentLitres(VW, pressureBarGauge, GasMix.AIR, IdealGas.INSTANCE);
        assertThat(actual)
                .as("content at %s bar", pressureBarGauge)
                .isCloseTo(expected, within(1e-4));
    }

    @Test
    void idealContentDoesNotDependOnTheMix() {
        for (final var mix : MIXES) {
            assertThat(GasContent.contentLitres(VW, 200, mix, IdealGas.INSTANCE))
                    .isCloseTo(2380.6158, within(1e-4));
        }
    }

    @Test
    void contentFollowsTheModelsZ() {
        final double pAbsBar = 200 + Units.P_ATM;
        final double z = 1 + 0.0008 * pAbsBar;
        final double expected = VW * pAbsBar / (z * Units.P_ATM);

        final double actual = GasContent.contentLitres(VW, 200, GasMix.HELIUM, LINEAR);

        assertThat(actual).isCloseTo(expected, within(1e-9));
        assertThat(actual).isLessThan(2380.6158);
    }

    // --- component litres ---

    @Test
    void componentLitresIsContentTimesFraction() {
        // 0.35 * 2380.6158 = 833.2155 = the He amount of the DOMAIN §11.3 target
        final double he =
                GasContent.componentLitres(
                        VW, 200, TRIMIX_21_35, IdealGas.INSTANCE, GasComponent.HE);
        final double o2 =
                GasContent.componentLitres(
                        VW, 200, TRIMIX_21_35, IdealGas.INSTANCE, GasComponent.O2);
        final double n2 =
                GasContent.componentLitres(
                        VW, 200, TRIMIX_21_35, IdealGas.INSTANCE, GasComponent.N2);

        assertThat(he).isCloseTo(833.2155, within(1e-4));
        assertThat(o2).isCloseTo(2380.6158 * 0.21, within(1e-4));
        assertThat(n2).isCloseTo(2380.6158 * 0.44, within(1e-4));
        assertThat(he + o2 + n2).isCloseTo(2380.6158, within(1e-4));
    }

    @Test
    void componentLitresOfAPureGasIsTheWholeContentOrZero() {
        final double content = GasContent.contentLitres(VW, 70, GasMix.OXYGEN, LINEAR);

        assertThat(GasContent.componentLitres(VW, 70, GasMix.OXYGEN, LINEAR, GasComponent.O2))
                .isEqualTo(content);
        assertThat(GasContent.componentLitres(VW, 70, GasMix.OXYGEN, LINEAR, GasComponent.HE))
                .isZero();
        assertThat(GasContent.componentLitres(VW, 70, GasMix.OXYGEN, LINEAR, GasComponent.N2))
                .isZero();
    }

    // --- pressureFor (the general inversion lives in CompressibilityModelContractTest) ---

    @Test
    void pressureForInvertsTheWorkedExampleContents() {
        // golden contents are rounded to 4 decimals: 5e-5 L is about 4e-6 bar here
        assertPressure(841.0155, 70);
        assertPressure(1101.5633, 92);
        assertPressure(2380.6158, 200);
    }

    private static void assertPressure(final double content, final double expectedBar) {
        assertThat(GasContent.pressureFor(VW, content, GasMix.AIR, IdealGas.INSTANCE))
                .isCloseTo(expectedBar, within(1e-4));
    }

    @Test
    void pressureForIsNonIdealAware() {
        final double content = GasContent.contentLitres(VW, 200, GasMix.HELIUM, LINEAR);

        // reading the same content as ideal gas would give a lower pressure
        assertThat(GasContent.pressureFor(VW, content, GasMix.HELIUM, LINEAR))
                .isCloseTo(200, within(1e-6));
        assertThat(GasContent.pressureFor(VW, content, GasMix.HELIUM, IdealGas.INSTANCE))
                .isLessThan(200 - 1);
    }

    // --- clamping and range ---

    @Test
    void contentAtOrBelowTheZeroBarContentReturnsZero() {
        for (final var model : MODELS) {
            final double atZero = GasContent.contentLitres(VW, 0, GasMix.AIR, model);

            assertThat(GasContent.pressureFor(VW, atZero, GasMix.AIR, model)).isZero();
            assertThat(GasContent.pressureFor(VW, atZero - 1e-9, GasMix.AIR, model)).isZero();
            assertThat(GasContent.pressureFor(VW, 5, GasMix.AIR, model)).isZero();
            assertThat(GasContent.pressureFor(VW, 0, GasMix.AIR, model)).isZero();
            assertThat(GasContent.pressureFor(VW, -250, GasMix.AIR, model)).isZero();
        }
    }

    @Test
    void contentJustAboveTheZeroBarContentIsASmallPositivePressure() {
        // d content / d p = Vw / P_ATM = 11.84 L/bar for IdealGas
        final double actual = GasContent.pressureFor(VW, 12.001, GasMix.AIR, IdealGas.INSTANCE);

        assertThat(actual).isCloseTo(0.001 * Units.P_ATM / VW, within(1e-6));
        assertThat(actual).isPositive();
    }

    @Test
    void contentAtTheThousandBarContentStillSolves() {
        for (final var model : MODELS) {
            final double atMax = GasContent.contentLitres(VW, 1000, GasMix.AIR, model);

            assertThat(GasContent.pressureFor(VW, atMax, GasMix.AIR, model))
                    .isCloseTo(1000, within(1e-6));
        }
    }

    @Test
    void contentAboveTheThousandBarContentThrows() {
        for (final var model : MODELS) {
            final double atMax = GasContent.contentLitres(VW, 1000, GasMix.AIR, model);

            assertThatThrownBy(() -> GasContent.pressureFor(VW, atMax + 1e-6, GasMix.AIR, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Gas content exceeds what the vessel holds at 1000 bar");
            assertThatThrownBy(() -> GasContent.pressureFor(VW, 1e9, GasMix.AIR, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Gas content exceeds what the vessel holds at 1000 bar");
        }
    }

    @Test
    void nonFiniteContentThrows() {
        for (final double bad :
                new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> GasContent.pressureFor(VW, bad, GasMix.AIR, IdealGas.INSTANCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Gas content must be a finite number");
        }
    }

    // --- input validation ---

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidWaterVolumeThrows(final double waterVolumeLitres) {
        final var message = "Water volume must be a number greater than 0";

        assertThatThrownBy(
                        () ->
                                GasContent.contentLitres(
                                        waterVolumeLitres, 100, GasMix.AIR, IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        assertThatThrownBy(
                        () ->
                                GasContent.componentLitres(
                                        waterVolumeLitres,
                                        100,
                                        GasMix.AIR,
                                        IdealGas.INSTANCE,
                                        GasComponent.O2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        assertThatThrownBy(
                        () ->
                                GasContent.pressureFor(
                                        waterVolumeLitres, 1000, GasMix.AIR, IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    @ParameterizedTest
    @ValueSource(
            doubles = {-0.001, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidPressureThrows(final double pressureBarGauge) {
        final var message = "Pressure must be a number of at least 0 bar";

        assertThatThrownBy(
                        () ->
                                GasContent.contentLitres(
                                        VW, pressureBarGauge, GasMix.AIR, IdealGas.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
        assertThatThrownBy(
                        () ->
                                GasContent.componentLitres(
                                        VW,
                                        pressureBarGauge,
                                        GasMix.AIR,
                                        IdealGas.INSTANCE,
                                        GasComponent.HE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }
}
