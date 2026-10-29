package ch.sthomas.stddivegastracker.calc.gas;

/** {@code Z ≡ 1}: hand-checkable tests and the DOMAIN §11 worked examples (§4.2). */
public final class IdealGas implements CompressibilityModel {

    public static final IdealGas INSTANCE = new IdealGas();

    private IdealGas() {}

    @Override
    public double z(final double pAbsBar, final GasMix mix) {
        return 1.0;
    }
}
