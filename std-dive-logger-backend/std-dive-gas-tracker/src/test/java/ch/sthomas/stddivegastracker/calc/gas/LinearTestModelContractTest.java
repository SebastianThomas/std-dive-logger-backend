package ch.sthomas.stddivegastracker.calc.gas;

/**
 * Test-only non-ideal model, {@code Z = 1 + (0.0002 + 0.0006·he)·pAbs} (He-heavy mixes deviate
 * more). Runs the contract with Z ≠ 1, so the suite is non-trivial before a production model such
 * as VirialFit exists.
 */
final class LinearTestModelContractTest extends CompressibilityModelContractTest {

    static final CompressibilityModel MODEL =
            (pAbsBar, mix) -> 1 + (0.0002 + 0.0006 * mix.he()) * pAbsBar;

    @Override
    protected CompressibilityModel model() {
        return MODEL;
    }
}
