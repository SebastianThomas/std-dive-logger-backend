package ch.sthomas.stddivegastracker.calc.gas;

/**
 * Production compressibility model (DOMAIN §4.2): per pure gas at 293.15 K, {@code Z_g - 1 = A·p +
 * B·p² + C·p³} (p = absolute bar), fitted to NIST isotherms up to 300 bar. Above that Z continues
 * linearly (value and slope at 300 bar), which keeps content monotone up to 1000 bar. Mixture:
 * {@code Z - 1 = Σ x_g·(Z_g - 1)}, an approximation good to ~1 % for diving mixes.
 */
public final class VirialFit implements CompressibilityModel {

    public static final VirialFit INSTANCE = new VirialFit();

    /** Stored on every fill; change it whenever the coefficients change. */
    public static final String MODEL_VERSION = "virial-nist-2026-1";

    // NIST WebBook isotherms at 293.15 K, 1-296 bar, fetched 2026-10-26; least squares by
    // tools/fit_compressibility.py (data: src/test/resources/nist). Max error vs NIST Z 0.13 %.
    private static final double HE_A = 4.868929e-04;
    private static final double HE_B = -5.985043e-08;
    private static final double HE_C = 2.300293e-11;
    private static final double O2_A = -7.441711e-04;
    private static final double O2_B = 2.026977e-06;
    private static final double O2_C = 8.390385e-10;
    private static final double N2_A = -2.767023e-04;
    private static final double N2_B = 3.054139e-06;
    private static final double N2_C = -1.892645e-09;

    private static final double FIT_MAX_BAR = 300.0;

    private VirialFit() {}

    @Override
    public double z(final double pAbsBar, final GasMix mix) {
        return 1
                + mix.fraction(GasComponent.O2) * excess(pAbsBar, O2_A, O2_B, O2_C)
                + mix.fraction(GasComponent.HE) * excess(pAbsBar, HE_A, HE_B, HE_C)
                + mix.fraction(GasComponent.N2) * excess(pAbsBar, N2_A, N2_B, N2_C);
    }

    /** {@code Z_g - 1}; beyond the fit range the tangent at {@link #FIT_MAX_BAR}. */
    private static double excess(
            final double pAbsBar, final double a, final double b, final double c) {
        final double fitBar = Math.min(pAbsBar, FIT_MAX_BAR);
        final double value = fitBar * (a + fitBar * (b + fitBar * c));
        final double slope = a + fitBar * (2 * b + 3 * c * fitBar);
        return value + slope * (pAbsBar - fitBar);
    }
}
