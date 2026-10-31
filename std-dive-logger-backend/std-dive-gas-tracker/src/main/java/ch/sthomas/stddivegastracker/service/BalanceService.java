package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.VesselAllocation;
import ch.sthomas.stddivegastracker.calc.scrubber.ScrubberPricing;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator;
import ch.sthomas.stddivegastracker.calc.settlement.SettlementSuggester;
import ch.sthomas.stddivegastracker.data.service.PaymentDataService;
import ch.sthomas.stddivegastracker.data.service.ScrubberDataService;
import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.Money;
import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.payment.Balances.MemberBalance;
import ch.sthomas.stddivegastracker.model.payment.Balances.Suggestion;
import ch.sthomas.stddivegastracker.model.payment.StoredPayment;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API.md §5 {@code GET …/balances}: the {@link TeamSnapshotService} state plus the team's payments
 * through {@link BalanceCalculator} and {@link SettlementSuggester}; money is rounded here, at the
 * boundary (DOMAIN §3).
 */
@Service
public final class BalanceService {

    private static final Comparator<MemberBalance> ORDER =
            Comparator.comparing(
                            (MemberBalance row) -> row.member().displayName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(row -> row.member().memberId());

    private final TeamAccessService teamAccess;
    private final TeamDataService teamDataService;
    private final PaymentDataService paymentDataService;
    private final ScrubberDataService scrubberDataService;
    private final TeamSnapshotService snapshots;

    public BalanceService(
            final TeamAccessService teamAccess,
            final TeamDataService teamDataService,
            final PaymentDataService paymentDataService,
            final ScrubberDataService scrubberDataService,
            final TeamSnapshotService snapshots) {
        this.teamAccess = teamAccess;
        this.teamDataService = teamDataService;
        this.paymentDataService = paymentDataService;
        this.scrubberDataService = scrubberDataService;
        this.snapshots = snapshots;
    }

    /**
     * Every member but the LEFT ones at 0.00 (DOMAIN §8), by name then id. The suggestions run over
     * all rows: a hidden 0.00 neither owes nor is owed.
     */
    public Balances balances(final long teamId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        // Payments first: members are never removed, so everyone they name is in the snapshot.
        final var payments = calcPayments(paymentDataService.list(teamId));
        final var currency = teamDataService.getTeam(caller).currency();
        final var state = snapshots.snapshot(caller);

        final var canisters = ScrubberService.calcCanisters(scrubberDataService.list(teamId));
        final var rows =
                BalanceCalculator.calculate(
                        state.input(),
                        state.allocation(),
                        payments,
                        ScrubberPricing.memberTotals(canisters));
        final var rowsByMember =
                rows.stream()
                        .collect(
                                Collectors.toMap(
                                        BalanceCalculator.MemberBalance::memberId,
                                        Function.identity()));
        final var shown = new ArrayList<MemberBalance>();
        for (final var member : state.facts().members()) {
            final var row = rowsByMember.getOrDefault(member.memberId(), idle(member.memberId()));
            final var balance = rounded(state.member(member.memberId()), row);
            if (member.status() != MemberStatus.LEFT || balance.balance().signum() != 0) {
                shown.add(balance);
            }
        }
        shown.sort(ORDER);

        final var suggestions =
                SettlementSuggester.suggest(rows).stream()
                        .map(
                                transfer ->
                                        new Suggestion(
                                                state.member(transfer.fromMemberId()),
                                                state.member(transfer.toMemberId()),
                                                transfer.amount()))
                        .toList();
        return new Balances(
                currency,
                shown,
                Money.amount(
                        state.allocation().vessels().stream()
                                .map(VesselAllocation::unallocated)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .add(
                                        canisters.stream()
                                                .map(
                                                        canister ->
                                                                ScrubberPricing.price(canister)
                                                                        .unallocated())
                                                .reduce(BigDecimal.ZERO, BigDecimal::add))),
                suggestions);
    }

    /** The team's payments as {@link BalanceCalculator} input (also used by the reports). */
    static List<BalanceCalculator.Payment> calcPayments(final List<StoredPayment> payments) {
        return payments.stream()
                .map(
                        payment ->
                                new BalanceCalculator.Payment(
                                        payment.fromMemberId(),
                                        payment.toMemberId(),
                                        payment.amount()))
                .toList();
    }

    /** A member the calculator has not heard of: nothing paid, charged, shared or settled. */
    private static BalanceCalculator.MemberBalance idle(final long memberId) {
        return new BalanceCalculator.MemberBalance(
                memberId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static MemberBalance rounded(
            final MemberRef member, final BalanceCalculator.MemberBalance row) {
        return new MemberBalance(
                member,
                Money.amount(row.paidCosts()),
                Money.amount(row.fillCharges()),
                Money.amount(row.lossShares()),
                Money.amount(row.scrubberPaid()),
                Money.amount(row.scrubberCharges()),
                Money.amount(row.paymentsSent()),
                Money.amount(row.paymentsReceived()),
                Money.amount(row.balance()),
                Money.amount(row.provisionalCharges()));
    }
}
