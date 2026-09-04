package ch.sthomas.stddivelogger.model.dive.home;

/**
 * How the diver's dive frequency over the last ~90 days compares to the ~90 days before it - used
 * to soften or sharpen the "time to go diving again" nudge (someone already tapering off isn't
 * "overdue", someone diving weekly who suddenly stops is).
 */
public enum CadenceTrend {
    PICKING_UP,
    STEADY,
    SLOWING,
    UNKNOWN
}
