package ch.sthomas.stddivelogger.model.dive.home;

/**
 * How strongly (if at all) to nudge the diver that it's time to go diving again. The thresholds are
 * per-diver and dynamic - a multiple of their own recent cadence, not a fixed number of days - see
 * {@code DiverActivityStatsDataService}.
 *
 * <ul>
 *   <li>{@link #NONE} - within their normal rhythm, no nudge.
 *   <li>{@link #GENTLE} - a bit past their usual interval; a soft "been a little while".
 *   <li>{@link #KEEN} - clearly past it; the main "time to plan the next one" nudge (this is the
 *       one that also triggers a push).
 *   <li>{@link #DORMANT} - so far past it (months / years) that they're evidently on a break; drop
 *       back to a single soft "still thinking about diving?" and stop pushing.
 * </ul>
 */
public enum NudgeLevel {
    NONE,
    GENTLE,
    KEEN,
    DORMANT
}
