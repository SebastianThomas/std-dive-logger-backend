package ch.sthomas.stddivegastracker.calc.gas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class IdealGasTest extends CompressibilityModelContractTest {

    @Override
    protected CompressibilityModel model() {
        return IdealGas.INSTANCE;
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void zIsExactlyOneEverywhere(final GasMix mix) {
        for (int pAbsBar = 0; pAbsBar <= 1001; pAbsBar++) {
            assertThat(IdealGas.INSTANCE.z(pAbsBar, mix)).isEqualTo(1.0);
        }
    }
}
