package ch.sthomas.stddivegastracker.model.payment;

import ch.sthomas.stddivegastracker.model.team.MemberRef;

import java.math.BigDecimal;
import java.util.List;

/**
 * API.md §5 {@code Balances}: every amount rounded {@code HALF_EVEN} to 2 decimals from unrounded
 * sums (DOMAIN §3), so the rows may differ from {@code inventoryValue} by a cent each (§7.5).
 */
public record Balances(
        String currency,
        List<MemberBalance> members,
        BigDecimal inventoryValue,
        List<Suggestion> suggestions) {

    public Balances {
        members = List.copyOf(members);
        suggestions = List.copyOf(suggestions);
    }

    /**
     * {@code provisionalCharges}: the part of {@code fillCharges} and {@code scrubberCharges} that
     * is still an estimate.
     */
    public record MemberBalance(
            MemberRef member,
            BigDecimal paidCosts,
            BigDecimal fillCharges,
            BigDecimal lossShares,
            BigDecimal scrubberPaid,
            BigDecimal scrubberCharges,
            BigDecimal paymentsSent,
            BigDecimal paymentsReceived,
            BigDecimal balance,
            BigDecimal provisionalCharges) {}

    /** {@code from} pays {@code to} (DOMAIN §8). */
    public record Suggestion(MemberRef from, MemberRef to, BigDecimal amount) {}
}
