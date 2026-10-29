package ch.sthomas.stddivegastracker.model.payment;

import ch.sthomas.stddivegastracker.model.team.MemberRef;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** API.md §5 {@code Payment}. */
public record Payment(
        long paymentId,
        MemberRef from,
        MemberRef to,
        BigDecimal amount,
        Instant paidAt,
        @Nullable String note,
        MemberRef createdBy) {}
