package ch.sthomas.stddivegastracker.calc.gas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

final class VirialFitTest extends CompressibilityModelContractTest {

    private static final GasMix NITROGEN = new GasMix(0, 0);
    private static final CompressibilityModel FIT = VirialFit.INSTANCE;

    @Override
    protected CompressibilityModel model() {
        return FIT;
    }

    @ParameterizedTest
    @CsvSource({"he,0,1", "o2,1,0", "n2,0,0"})
    void zIsWithinPointThreePercentOfEveryNistRow(
            final String gas, final double o2, final double he) throws IOException {
        final var pureGas = new GasMix(o2, he);
        final var rows = nistRows(gas);

        assertThat(rows).hasSizeGreaterThanOrEqualTo(60);
        for (final var row : rows) {
            final double pAbsBar = Double.parseDouble(row[0]);
            final double nistZ = Double.parseDouble(row[2]);

            assertThat(FIT.z(pAbsBar, pureGas))
                    .as("%s at %s bar abs", gas, pAbsBar)
                    .isCloseTo(nistZ, withinPercentage(0.3));
        }
    }

    @Test
    void heliumAt201BarAbsoluteMatchesDomainSanityValue() {
        assertThat(FIT.z(201, GasMix.HELIUM)).isCloseTo(1.0956, within(0.002));
    }

    @Test
    void mixtureIsMoleFractionWeightedSumOfPureGasDeviations() {
        final double pAbsBar = 200 + Units.P_ATM;
        final double expected =
                1
                        + 0.21 * (FIT.z(pAbsBar, GasMix.OXYGEN) - 1)
                        + 0.35 * (FIT.z(pAbsBar, GasMix.HELIUM) - 1)
                        + 0.44 * (FIT.z(pAbsBar, NITROGEN) - 1);

        assertThat(FIT.z(pAbsBar, new GasMix(0.21, 0.35))).isCloseTo(expected, within(1e-12));
    }

    @Test
    void extensionAbove300BarIsContinuousInValueAndSlope() {
        final double valueStep = 1e-7;
        final double slopeStep = 1e-3;
        for (final var mix :
                List.of(GasMix.HELIUM, GasMix.OXYGEN, NITROGEN, new GasMix(0.21, 0.35))) {
            final double at300 = FIT.z(300, mix);
            final double slopeBelow = (at300 - FIT.z(300 - slopeStep, mix)) / slopeStep;
            final double slopeAbove = (FIT.z(300 + slopeStep, mix) - at300) / slopeStep;

            assertThat(FIT.z(300 - valueStep, mix)).as("%s", mix).isCloseTo(at300, within(1e-9));
            assertThat(FIT.z(300 + valueStep, mix)).as("%s", mix).isCloseTo(at300, within(1e-9));
            assertThat(slopeAbove).as("slope %s", mix).isCloseTo(slopeBelow, within(1e-6));
        }
    }

    /** Rows of {@code nist/<gas>-293K.tsv} as pressure bar, density mol/l, Z; '#' lines skipped. */
    private static List<String[]> nistRows(final String gas) throws IOException {
        try (final var reader =
                new BufferedReader(
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        VirialFitTest.class.getResourceAsStream(
                                                "/nist/" + gas + "-293K.tsv")),
                                StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.startsWith("#"))
                    .map(l -> l.split("\t"))
                    .toList();
        }
    }
}
