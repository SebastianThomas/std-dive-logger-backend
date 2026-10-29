package ch.sthomas.stddivegastracker.model.vessel;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API.md §3 {@code CostLineInput}. Money: above 0, below 1e9, at most 2 decimals (DOMAIN §3).
 * Absent {@code payerMemberId} is 0, which no member has (400 "Unknown payer" in the service).
 */
public record CostLineInput(
        long payerMemberId, BigDecimal amount, @Nullable Instant paidAt, @Nullable String note) {

    private static final int MAX_NOTE_LENGTH = 200;

    public CostLineInput {
        amount = Limits.money(amount);
        note = Limits.text(note, MAX_NOTE_LENGTH, "Note");
    }
}
