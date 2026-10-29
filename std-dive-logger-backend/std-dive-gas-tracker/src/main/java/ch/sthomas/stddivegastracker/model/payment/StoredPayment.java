package ch.sthomas.stddivegastracker.model.payment;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/** A payment as stored (DOMAIN §2): members by id, what the data layer returns. */
public record StoredPayment(
        long paymentId,
        long fromMemberId,
        long toMemberId,
        BigDecimal amount,
        Instant paidAt,
        @Nullable String note,
        long createdByMemberId) {}
