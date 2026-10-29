package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.data.service.PaymentDataService;
import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.payment.Payment;
import ch.sthomas.stddivegastracker.model.payment.PaymentInput;
import ch.sthomas.stddivegastracker.model.payment.StoredPayment;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * API.md §5 payments; authorization per DOMAIN §10: the payer, the recipient or an ADMIN records
 * and deletes. Balances are {@link BalanceService}'s.
 */
@Service
public final class PaymentService {

    private final TeamAccessService teamAccess;
    private final TeamDataService teamDataService;
    private final PaymentDataService paymentDataService;

    public PaymentService(
            final TeamAccessService teamAccess,
            final TeamDataService teamDataService,
            final PaymentDataService paymentDataService) {
        this.teamAccess = teamAccess;
        this.teamDataService = teamDataService;
        this.paymentDataService = paymentDataService;
    }

    /** Newest first, the whole list. */
    public List<Payment> list(final long teamId, final GasUser user) {
        teamAccess.requireMember(teamId, user);
        // Payments first: members are never removed, so everyone they name is read afterwards.
        final var payments = paymentDataService.list(teamId);
        final var members = membersOf(teamId);
        return payments.stream().map(payment -> payment(payment, members)).toList();
    }

    /** Counts at once (DOMAIN §8); both parties must belong to the team, in any status. */
    public Payment record(final long teamId, final GasUser user, final PaymentInput request) {
        final var caller = teamAccess.requireMember(teamId, user);
        requireMayChange(caller, request.fromMemberId(), request.toMemberId(), "record");
        final var stored = paymentDataService.create(teamId, caller.memberId(), request);
        return payment(stored, membersOf(teamId));
    }

    public void delete(final long teamId, final long paymentId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var stored = paymentDataService.get(teamId, paymentId);
        requireMayChange(caller, stored.fromMemberId(), stored.toMemberId(), "delete");
        paymentDataService.delete(teamId, paymentId);
    }

    /** DOMAIN §10: the payer, the recipient or an ADMIN. */
    private static void requireMayChange(
            final TeamMembership caller,
            final long fromMemberId,
            final long toMemberId,
            final String action) {
        if (caller.role() != Role.ADMIN
                && caller.memberId() != fromMemberId
                && caller.memberId() != toMemberId) {
            throw new AccessDeniedException(
                    "Only a team admin, the payer or the recipient can " + action + " a payment.");
        }
    }

    /** Everyone in the team, LEFT included (they can be a party). */
    private Map<Long, MemberRef> membersOf(final long teamId) {
        return teamDataService.members(teamId).stream()
                .collect(
                        Collectors.toMap(
                                Member::memberId,
                                member -> new MemberRef(member.memberId(), member.displayName())));
    }

    private static Payment payment(final StoredPayment stored, final Map<Long, MemberRef> members) {
        return new Payment(
                stored.paymentId(),
                member(members, stored.fromMemberId()),
                member(members, stored.toMemberId()),
                stored.amount(),
                stored.paidAt(),
                stored.note(),
                member(members, stored.createdByMemberId()));
    }

    private static MemberRef member(final Map<Long, MemberRef> members, final long memberId) {
        return Objects.requireNonNull(members.get(memberId), "No member " + memberId);
    }
}
