package ch.sthomas.stddivegastracker.calc.gas;

/**
 * Compressibility factor Z at {@link Units#T_REF_KELVIN} (DOMAIN §4.2). Content must increase
 * monotonically with pressure, or {@link GasContent#pressureFor} is not well defined (§4.3).
 */
public interface CompressibilityModel {

    /** Z for the given absolute pressure (bar) and mix; positive and finite. */
    double z(double pAbsBar, GasMix mix);
}
