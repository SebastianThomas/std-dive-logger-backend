package ch.sthomas.stddivegastracker.calc.gas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class GasMixTest {

    // --- validation (DOMAIN §3: each input, not only the derived sum) ---

    @ParameterizedTest
    @CsvSource({"0.6, 0.5", "1, 0.00000001", "0.5, 0.50000001", "0.9, 0.9"})
    void sumAboveOneIsRejected(final double o2, final double he) {
        assertThatThrownBy(() -> new GasMix(o2, he))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O2 and He fractions must not add up to more than 1");
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "0, 1", "0.5, 0.5", "0, 0", "1, 0.0000000001", "0.5, 0.5000000001"})
    void sumUpToOnePlusToleranceIsAccepted(final double o2, final double he) {
        assertThatCode(() -> new GasMix(o2, he)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, -1e-12, -1})
    void negativeO2IsRejected(final double o2) {
        assertThatThrownBy(() -> new GasMix(o2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O2 fraction must be between 0 and 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, -1e-12, -1})
    void negativeHeIsRejected(final double he) {
        assertThatThrownBy(() -> new GasMix(0.2, he))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("He fraction must be between 0 and 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0000001, 1.1, 50})
    void o2AboveOneIsRejected(final double o2) {
        assertThatThrownBy(() -> new GasMix(o2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O2 fraction must be between 0 and 1");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1.0000001, 1.1, 50})
    void heAboveOneIsRejected(final double he) {
        assertThatThrownBy(() -> new GasMix(0, he))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("He fraction must be between 0 and 1");
    }

    @Test
    void eachInputIsValidatedEvenWhenTheSumLooksFine() {
        // sum = 1 and sum = 0.4: a derived-remainder or sum-only check would accept both
        assertThatThrownBy(() -> new GasMix(1.2, -0.2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("O2");
        assertThatThrownBy(() -> new GasMix(-0.1, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("O2");
        assertThatThrownBy(() -> new GasMix(0.5, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("He");
    }

    @Test
    void nonFiniteInputsAreRejected() {
        for (final double bad :
                new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new GasMix(bad, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("O2 fraction must be a finite number");
            assertThatThrownBy(() -> new GasMix(0.2, bad))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("He fraction must be a finite number");
        }
    }

    // --- n2 and fraction ---

    @Test
    void n2IsTheRemainder() {
        assertThat(GasMix.AIR.n2()).isCloseTo(0.791, within(1e-12));
        assertThat(new GasMix(0.18, 0.45).n2()).isCloseTo(0.37, within(1e-12));
        assertThat(GasMix.OXYGEN.n2()).isZero();
        assertThat(GasMix.HELIUM.n2()).isZero();
    }

    @Test
    void n2ClampsFloatNoiseBelowZero() {
        final var mix = new GasMix(0.5, 0.5000000001);
        assertThat(mix.n2()).isZero();
        assertThat(new GasMix(1, 0.0000000001).n2()).isZero();
    }

    @Test
    void fractionReturnsEachComponent() {
        final var mix = new GasMix(0.21, 0.35);
        assertThat(mix.fraction(GasComponent.O2)).isEqualTo(0.21);
        assertThat(mix.fraction(GasComponent.HE)).isEqualTo(0.35);
        assertThat(mix.fraction(GasComponent.N2)).isCloseTo(0.44, within(1e-12));
    }

    // --- constants ---

    @Test
    void constantsMatchTheSpec() {
        assertThat(GasMix.AIR).isEqualTo(new GasMix(0.209, 0));
        assertThat(GasMix.OXYGEN).isEqualTo(new GasMix(1, 0));
        assertThat(GasMix.HELIUM).isEqualTo(new GasMix(0, 1));
        assertThat(Units.P_ATM).isEqualTo(1.01325);
        assertThat(Units.T_REF_KELVIN).isEqualTo(293.15);
    }

    // --- classification (DOMAIN §3) ---

    @ParameterizedTest
    @CsvSource({
        // OXYGEN: o2 >= 0.99
        "1, 0, OXYGEN",
        "0.99, 0, OXYGEN",
        "0.99, 0.01, OXYGEN",
        "0.989, 0, NITROX",
        // HELIUM: he >= 0.99
        "0, 1, HELIUM",
        "0, 0.99, HELIUM",
        "0.01, 0.99, HELIUM",
        "0, 0.989, TRIMIX",
        // AIR: |o2 - 0.209| <= 0.005 and he <= 0.005
        "0.209, 0, AIR",
        "0.21, 0, AIR",
        "0.2139, 0, AIR",
        "0.2041, 0, AIR",
        // exactly on the inclusive boundary (float noise must not flip it)
        "0.214, 0, AIR",
        "0.204, 0, AIR",
        "0.209, 0.005, AIR",
        "0.2141, 0, NITROX",
        "0.2039, 0, NITROX",
        "0.209, 0.0051, TRIMIX",
        // NITROX: he <= 0.005 (otherwise not air)
        "0.32, 0, NITROX",
        "0.36, 0, NITROX",
        "0.5, 0.005, NITROX",
        "0.18, 0, NITROX",
        // TRIMIX: everything else, incl. heliox and heliair
        "0.21, 0.35, TRIMIX",
        "0.18, 0.45, TRIMIX",
        "0.32, 0.0051, TRIMIX",
        "0.21, 0.79, TRIMIX",
        "0.209, 0.3, TRIMIX",
        "0.5, 0.5, TRIMIX"
    })
    void gasClassUsesTheSpecThresholds(final double o2, final double he, final GasClass expected) {
        assertThat(new GasMix(o2, he).gasClass()).isEqualTo(expected);
    }

    @Test
    void constantsAreClassifiedAsExpected() {
        assertThat(GasMix.AIR.gasClass()).isEqualTo(GasClass.AIR);
        assertThat(GasMix.OXYGEN.gasClass()).isEqualTo(GasClass.OXYGEN);
        assertThat(GasMix.HELIUM.gasClass()).isEqualTo(GasClass.HELIUM);
        assertThat(new GasMix(0.32, 0).gasClass()).isEqualTo(GasClass.NITROX);
        assertThat(new GasMix(0.21, 0.35).gasClass()).isEqualTo(GasClass.TRIMIX);
    }
}
