package ch.sthomas.stddivelogger.model.dive.gear;

/**
 * What a tracked cylinder was actually used for, which decides how it feeds into gas-consumption
 * calculations: a plain OC dive's cylinder(s) all count toward one whole-dive RMV; a CCR dive
 * splits differently - only {@link #BAILOUT} cylinders (breathed open-circuit) count toward bailout
 * RMV, while {@link #O2} and {@link #DILUENT} are tracked as separate injected-gas totals (in
 * litres, not an RMV - there's no continuous open-circuit breathing rate for gas added to a closed
 * loop).
 */
public enum CylinderRole {
    /** The only kind of cylinder a plain open-circuit dive has. */
    OC,
    /** A CCR dive's diluent supply. */
    DILUENT,
    /** A CCR dive's oxygen supply, injected to maintain setpoint. */
    O2,
    /** A CCR dive's open-circuit bailout gas. */
    BAILOUT;
}
