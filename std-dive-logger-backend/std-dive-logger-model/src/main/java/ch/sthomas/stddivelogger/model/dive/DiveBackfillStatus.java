package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * How complete one dive's optional-but-useful fields are, for the "backfill" flow that guides a
 * user through sparsely-logged/pre-existing dives instead of making them search for gaps
 * themselves. {@code missingFields} lists the checklist keys ({@code VISIBILITY},
 * {@code GAS_CONSUMPTION}, {@code WATER_TYPE}, {@code LEADER}, {@code NOTES}) this dive hasn't had
 * filled in yet - a dive with none listed is fully backfilled and won't appear in the queue.
 */
public record DiveBackfillStatus(
        long diveId,
        int number,
        String diveIdentifier,
        @Nullable Instant diveStart,
        List<String> missingFields) {

    public int missingCount() {
        return missingFields.size();
    }
}
