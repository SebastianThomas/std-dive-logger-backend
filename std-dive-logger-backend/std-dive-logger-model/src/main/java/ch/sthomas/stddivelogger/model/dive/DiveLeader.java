package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

/**
 * Who led a dive - either the dive's own owner having explicitly confirmed that ({@code SELF}, both
 * fields null), a named buddy ({@code namedBuddyId} set), the diver who owns a linked buddy dive
 * ({@code linkedDiveId} set, meaning "the diver who owns that linked dive led"), or nobody having
 * ever made a choice at all ({@code UNSET}, both fields null too). {@code UNSET} exists so the UI
 * never states "X led the dive" as a confirmed fact for a dive whose owner simply never opened the
 * leader picker - collapsing that into {@code SELF} (as this type used to) made every
 * pre-existing/imported dive silently claim its owner led it.
 */
public record DiveLeader(
        LeaderType type, @Nullable Long namedBuddyId, @Nullable Long linkedDiveId) {
    public enum LeaderType {
        SELF,
        NAMED,
        LINKED,
        UNSET
    }

    public static final DiveLeader SELF = new DiveLeader(LeaderType.SELF, null, null);
    public static final DiveLeader UNSET = new DiveLeader(LeaderType.UNSET, null, null);
}
