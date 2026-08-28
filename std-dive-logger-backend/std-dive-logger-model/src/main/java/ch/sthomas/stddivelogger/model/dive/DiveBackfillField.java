package ch.sthomas.stddivelogger.model.dive;

/**
 * The enumerated set of "reasons" a dive can show up in the backfill guide - one per optional-but-
 * useful field the guide nudges the user to fill in. Adding a value here is how a new backfillable
 * feature is registered: {@link DiveBackfillStatus} starts reporting it, and every dive missing it
 * surfaces automatically (a {@code t_dive_backfill_dismissal} row per (dive, reason) is what
 * suppresses one - see {@code DiveBackfillDismissalEntity}).
 */
public enum DiveBackfillField {
    VISIBILITY,
    GAS_CONSUMPTION,
    LEADER,
    NOTES
}
