package ch.sthomas.stddivelogger.model.dive.profile.measurement;

public record CylinderSize(CylinderSizeUnit unit, double value) {

    private static final double LITERS_PER_CUBIC_FOOT = 28.31685;
    private static final double BAR_PER_ATM = 1.01325;
    // US "cuft" ratings are free-gas capacity at the cylinder's rated service pressure, not water
    // volume. The per-cylinder service pressure isn't stored, so approximate it as 3000 psi
    // (~206.8 bar) - right for the common AL80/AL40 aluminium tanks, a bit off for LP/HP steels.
    private static final double ASSUMED_SERVICE_PRESSURE_BAR = 206.843;
    private static final double LITERS_PER_CUFT_RATING =
            LITERS_PER_CUBIC_FOOT * BAR_PER_ATM / ASSUMED_SERVICE_PRESSURE_BAR; // ~0.1387

    /**
     * Water volume in litres - the figure gas-consumption maths needs (litres consumed at surface =
     * bar drop x this). For a {@code CUFT} size this is the derived water volume, not the raw cuft
     * number x litres-per-cubic-foot (which is ~200x too large - it treats a free-gas rating as if
     * it were a physical volume).
     */
    public double liters() {
        return switch (unit) {
            case LITER -> value;
            case CUFT -> value * LITERS_PER_CUFT_RATING;
        };
    }
}
