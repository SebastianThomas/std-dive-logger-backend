package ch.sthomas.stddivegastracker.model.fill;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A fill as stored: its content plus ids, timestamps and the void state (VOIDED = voidedAt). */
public record StoredFill(
        long fillId,
        long recordedByMemberId,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant voidedAt,
        @Nullable String voidReason,
        FillContent content) {

    public boolean isVoided() {
        return voidedAt != null;
    }
}
