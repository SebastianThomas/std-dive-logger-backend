package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

/**
 * Who led a dive - either the dive's own owner (both fields null), a named buddy ({@code
 * namedBuddyId} set), or the diver who owns a linked buddy dive ({@code linkedDiveId} set, meaning
 * "the diver who owns that linked dive led").
 */
public record DiveLeader(
        LeaderType type, @Nullable Long namedBuddyId, @Nullable Long linkedDiveId) {
    public enum LeaderType {
        SELF,
        NAMED,
        LINKED
    }

    public static final DiveLeader SELF = new DiveLeader(LeaderType.SELF, null, null);
}
