package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

/**
 * The dive immediately before/after a given dive in its owner's own number sequence - {@code null}
 * on either side when the given dive is already the first/last. Deliberately scoped to a single
 * user's dives (numbers are only unique per user), so this only ever makes sense for a dive's owner
 * browsing their own log, not a shared/reader view of someone else's dive.
 */
public record AdjacentDives(@Nullable Long previousDiveId, @Nullable Long nextDiveId) {}
