package ch.sthomas.stddivegastracker.calc.gas;

/** Vessel content in surface litres and its inverse (DOMAIN §4.1, §4.3). */
public final class GasContent {

    /** Upper end of {@link #pressureFor}'s search range (DOMAIN §4.3). */
    public static final double MAX_PRESSURE_BAR = 1000.0;

    private static final double PRESSURE_TOLERANCE_BAR = 1e-6;
    private static final int MAX_ITERATIONS = 200;

    private GasContent() {}

    /** {@code Vw · pAbs / (Z · P_ATM)}; an empty cylinder at 0 bar still holds Vw litres. */
    public static double contentLitres(
            final double waterVolumeLitres,
            final double pressureBarGauge,
            final GasMix mix,
            final CompressibilityModel model) {
        requireWaterVolume(waterVolumeLitres);
        requirePressure(pressureBarGauge);
        final double pAbsBar = pressureBarGauge + Units.P_ATM;
        return waterVolumeLitres * pAbsBar / (model.z(pAbsBar, mix) * Units.P_ATM);
    }

    public static double componentLitres(
            final double waterVolumeLitres,
            final double pressureBarGauge,
            final GasMix mix,
            final CompressibilityModel model,
            final GasComponent component) {
        return contentLitres(waterVolumeLitres, pressureBarGauge, mix, model)
                * mix.fraction(component);
    }

    /**
     * Gauge pressure holding {@code contentLitres}, by bisection on [0, 1000] bar. At or below the
     * 0-bar content returns 0 (overdrawn estimates must not throw); above the 1000-bar content
     * throws.
     */
    public static double pressureFor(
            final double waterVolumeLitres,
            final double contentLitres,
            final GasMix mix,
            final CompressibilityModel model) {
        requireWaterVolume(waterVolumeLitres);
        if (!Double.isFinite(contentLitres)) {
            throw new IllegalArgumentException("Gas content must be a finite number");
        }
        if (contentLitres <= contentLitres(waterVolumeLitres, 0, mix, model)) {
            return 0.0;
        }
        if (contentLitres > contentLitres(waterVolumeLitres, MAX_PRESSURE_BAR, mix, model)) {
            throw new IllegalArgumentException(
                    "Gas content exceeds what the vessel holds at 1000 bar");
        }
        double lowBar = 0.0;
        double highBar = MAX_PRESSURE_BAR;
        for (int i = 0; i < MAX_ITERATIONS && highBar - lowBar > PRESSURE_TOLERANCE_BAR; i++) {
            final double midBar = (lowBar + highBar) / 2;
            if (contentLitres(waterVolumeLitres, midBar, mix, model) < contentLitres) {
                lowBar = midBar;
            } else {
                highBar = midBar;
            }
        }
        return (lowBar + highBar) / 2;
    }

    private static void requireWaterVolume(final double waterVolumeLitres) {
        if (!Double.isFinite(waterVolumeLitres) || waterVolumeLitres <= 0) {
            throw new IllegalArgumentException("Water volume must be a number greater than 0");
        }
    }

    private static void requirePressure(final double pressureBarGauge) {
        if (!Double.isFinite(pressureBarGauge) || pressureBarGauge < 0) {
            throw new IllegalArgumentException("Pressure must be a number of at least 0 bar");
        }
    }
}
