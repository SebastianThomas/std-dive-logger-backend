package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * How complete one dive's optional-but-useful fields are, for the "backfill" flow that guides a
 * user through sparsely-logged/pre-existing dives instead of making them search for gaps
 * themselves.
 *
 * <p>{@code missingFields} lists every {@link DiveBackfillField} this dive hasn't had filled in
 * yet; {@code dismissedFields} lists the ones the user has explicitly marked "no more info to add"
 * (a {@code t_dive_backfill_dismissal} row per (dive, reason)). {@link #outstandingFields()} -
 * missing minus dismissed - is what actually still needs attention: a dive with none is either
 * fully filled in or fully dismissed and drops out of the active queue.
 */
public record DiveBackfillStatus(
        long diveId,
        int number,
        String diveIdentifier,
        @Nullable Instant diveStart,
        List<DiveBackfillField> missingFields,
        List<DiveBackfillField> dismissedFields) {

    public int missingCount() {
        return missingFields.size();
    }

    /** Missing fields the user hasn't dismissed - the real "still to do" list. */
    public List<DiveBackfillField> outstandingFields() {
        return missingFields.stream().filter(f -> !dismissedFields.contains(f)).toList();
    }

    public int outstandingCount() {
        return outstandingFields().size();
    }

    /**
     * This dive has real gaps, but the user has dismissed all of them - it belongs in the "no more
     * info to add" section rather than the active queue.
     */
    public boolean fullyDismissed() {
        return !missingFields.isEmpty() && outstandingFields().isEmpty();
    }
}
