package ch.sthomas.stddivelogger.model.dive.profile.measurement;

/**
 * Whether a rebreather diver was on the closed loop (CC) or breathing open-circuit (OC/bailout) at
 * a given measurement. Absent on OC-only dives and on sources that don't report it (FIT).
 */
public enum DiveMode {
    OC,
    CC
}
