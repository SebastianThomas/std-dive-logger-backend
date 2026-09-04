package ch.sthomas.stddivelogger.model.dive.home;

/** The kind of {@link DiverReminder}. */
public enum ReminderKind {
    /** "N years ago today you dived at ..." - built from the diver's own dive history. */
    DIVE_ANNIVERSARY,
    /** "It's been a while since your last dive" - fired off the dynamic per-diver cadence. */
    DIVE_AGAIN_NUDGE
}
