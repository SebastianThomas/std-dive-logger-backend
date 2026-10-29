package ch.sthomas.stddivegastracker.calc.gas;

/**
 * Gas mix as fractions of O2 and He; N2 is the remainder (DOMAIN §3). Each input is validated, not
 * only the derived sum.
 */
public record GasMix(double o2, double he) {

    public static final GasMix AIR = new GasMix(0.209, 0);
    public static final GasMix OXYGEN = new GasMix(1, 0);
    public static final GasMix HELIUM = new GasMix(0, 1);

    private static final double SUM_TOLERANCE = 1e-9;
    private static final double PURE_MIN_FRACTION = 0.99;
    private static final double AIR_O2 = 0.209;
    private static final double AIR_O2_TOLERANCE = 0.005;
    private static final double TRACE_HE_MAX = 0.005;
    // Thresholds are inclusive decimals: absorb float noise (|0.214 - 0.209| > 0.005 in doubles).
    private static final double CLASS_EPSILON = 1e-9;

    public GasMix {
        requireFraction(o2, "O2");
        requireFraction(he, "He");
        if (o2 + he > 1 + SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "O2 and He fractions must not add up to more than 1");
        }
    }

    private static void requireFraction(final double value, final String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " fraction must be a finite number");
        }
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " fraction must be between 0 and 1");
        }
    }

    /** Remainder fraction; tiny negative float noise (within the sum tolerance) clamps to 0. */
    public double n2() {
        return Math.max(0.0, 1.0 - o2 - he);
    }

    public double fraction(final GasComponent component) {
        return switch (component) {
            case O2 -> o2;
            case HE -> he;
            case N2 -> n2();
        };
    }

    /** Report classification, thresholds in DOMAIN §3 (checked in the order listed there). */
    public GasClass gasClass() {
        if (o2 >= PURE_MIN_FRACTION - CLASS_EPSILON) {
            return GasClass.OXYGEN;
        }
        if (he >= PURE_MIN_FRACTION - CLASS_EPSILON) {
            return GasClass.HELIUM;
        }
        if (Math.abs(o2 - AIR_O2) <= AIR_O2_TOLERANCE + CLASS_EPSILON
                && he <= TRACE_HE_MAX + CLASS_EPSILON) {
            return GasClass.AIR;
        }
        if (he <= TRACE_HE_MAX + CLASS_EPSILON) {
            return GasClass.NITROX;
        }
        return GasClass.TRIMIX;
    }
}
