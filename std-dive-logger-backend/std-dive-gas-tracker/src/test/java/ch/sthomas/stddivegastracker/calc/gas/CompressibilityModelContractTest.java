package ch.sthomas.stddivegastracker.calc.gas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Contract every {@link CompressibilityModel} must satisfy (DOMAIN §4.2, §4.3). Each model's test
 * class extends this and supplies the model; a new model is not done until it passes.
 */
abstract class CompressibilityModelContractTest {

    private static final double VW = 12.0;

    protected abstract CompressibilityModel model();

    static Stream<GasMix> mixes() {
        return Stream.of(
                GasMix.AIR,
                GasMix.OXYGEN,
                GasMix.HELIUM,
                new GasMix(0.21, 0.35),
                new GasMix(0.10, 0.70),
                new GasMix(0.32, 0));
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void zIsFiniteAndPositiveUpTo1001BarAbsolute(final GasMix mix) {
        for (int pAbsBar = 1; pAbsBar <= 1001; pAbsBar++) {
            assertThat(model().z(pAbsBar, mix))
                    .as("Z at %s bar abs, %s", pAbsBar, mix)
                    .isFinite()
                    .isPositive();
        }
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void zAtOneAtmosphereIsAlmostOne(final GasMix mix) {
        assertThat(model().z(Units.P_ATM, mix)).isCloseTo(1.0, within(0.01));
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void contentIsStrictlyIncreasingInPressure(final GasMix mix) {
        double previous = GasContent.contentLitres(VW, 0, mix, model());
        for (int i = 1; i <= 2000; i++) {
            final double pressureBar = i * 0.5; // 0.5..1000 bar gauge
            final double content = GasContent.contentLitres(VW, pressureBar, mix, model());

            assertThat(content)
                    .as("content at %s bar, %s", pressureBar, mix)
                    .isGreaterThan(previous);
            previous = content;
        }
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void pressureForInvertsContentLitres(final GasMix mix) {
        for (final double waterVolumeLitres : new double[] {7, VW, 3000}) {
            for (int i = 0; i <= 1200; i++) {
                final double pressureBar = i * 0.25; // 0..300 bar gauge
                final double content =
                        GasContent.contentLitres(waterVolumeLitres, pressureBar, mix, model());

                assertThat(GasContent.pressureFor(waterVolumeLitres, content, mix, model()))
                        .as("Vw=%s, p=%s, %s", waterVolumeLitres, pressureBar, mix)
                        .isCloseTo(pressureBar, within(1e-6));
            }
        }
    }
}
