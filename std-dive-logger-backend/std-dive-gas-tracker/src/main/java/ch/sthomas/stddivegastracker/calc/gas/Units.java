package ch.sthomas.stddivegastracker.calc.gas;

/** Physical reference constants (DOMAIN §3). */
public final class Units {

    /** Standard atmosphere in bar; {@code pAbs = pGauge + P_ATM}. */
    public static final double P_ATM = 1.01325;

    /** Reference temperature, 20 °C; surface litres are normalised to it. */
    public static final double T_REF_KELVIN = 293.15;

    private Units() {}
}
