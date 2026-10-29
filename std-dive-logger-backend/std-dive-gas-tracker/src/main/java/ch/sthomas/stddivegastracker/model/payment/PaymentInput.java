package ch.sthomas.stddivegastracker.model.payment;

import ch.sthomas.stddivegastracker.model.vessel.Limits;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API.md §5 {@code POST …/payments} body (DOMAIN §8): two different members, money as in {@link
 * Limits#money}. Absent member ids are 0, which no member has (400 "Unknown member" in the data
 * service).
 */
public record PaymentInput(
        long fromMemberId,
        long toMemberId,
        BigDecimal amount,
        @Nullable Instant paidAt,
        @Nullable String note) {

    private static final int MAX_NOTE_LENGTH = 200;

    public PaymentInput {
        if (fromMemberId == toMemberId) {
            throw new IllegalArgumentException("A payment needs two different members.");
        }
        amount = Limits.money(amount);
        note = Limits.text(note, MAX_NOTE_LENGTH, "Note");
    }
}
