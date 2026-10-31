package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.scrubber.ScrubberPricing;
import ch.sthomas.stddivegastracker.data.service.ScrubberDataService;
import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.Money;
import ch.sthomas.stddivegastracker.model.scrubber.CanisterCreate;
import ch.sthomas.stddivegastracker.model.scrubber.CanisterStatus;
import ch.sthomas.stddivegastracker.model.scrubber.MarkEmptyRequest;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberCanister;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberUseInput;
import ch.sthomas.stddivegastracker.model.scrubber.StoredCanister;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API.md §7; rules per DOMAIN §12. Any member records a use and says a canister is empty (standing
 * at the canister, as with fills); a wrong use goes by its recorder, its member or an admin.
 */
@Service
public final class ScrubberService {

    private final TeamAccessService teamAccess;
    private final TeamDataService teamDataService;
    private final ScrubberDataService scrubberDataService;

    public ScrubberService(
            final TeamAccessService teamAccess,
            final TeamDataService teamDataService,
            final ScrubberDataService scrubberDataService) {
        this.teamAccess = teamAccess;
        this.teamDataService = teamDataService;
        this.scrubberDataService = scrubberDataService;
    }

    public List<ScrubberCanister> list(final long teamId, final GasUser user) {
        teamAccess.requireMember(teamId, user);
        final var members = members(teamId);
        return scrubberDataService.list(teamId).stream()
                .map(canister -> canisterOf(canister, members))
                .toList();
    }

    public ScrubberCanister get(final long teamId, final long canisterId, final GasUser user) {
        teamAccess.requireMember(teamId, user);
        return canisterOf(scrubberDataService.get(teamId, canisterId), members(teamId));
    }

    public ScrubberCanister create(
            final long teamId, final GasUser user, final CanisterCreate request) {
        final var caller = requireTracking(teamId, user);
        for (final var line : request.initialCostLines()) {
            requirePayerAllowed(caller, user, line);
        }
        return canisterOf(scrubberDataService.create(caller, request), members(teamId));
    }

    public ScrubberCanister addUse(
            final long teamId,
            final long canisterId,
            final GasUser user,
            final ScrubberUseInput input) {
        final var caller = requireTracking(teamId, user);
        return canisterOf(scrubberDataService.addUse(caller, canisterId, input), members(teamId));
    }

    public ScrubberCanister deleteUse(
            final long teamId, final long canisterId, final long useId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var use =
                scrubberDataService.get(teamId, canisterId).uses().stream()
                        .filter(candidate -> candidate.useId() == useId)
                        .findFirst();
        if (use.isPresent()
                && caller.role() != Role.ADMIN
                && use.get().memberId() != caller.memberId()
                && use.get().recordedByMemberId() != caller.memberId()) {
            throw new AccessDeniedException(
                    "Only a team admin, the member who recorded this or the member it is for can"
                            + " delete it.");
        }
        return canisterOf(
                scrubberDataService.deleteUse(teamId, canisterId, useId), members(teamId));
    }

    public ScrubberCanister markEmpty(
            final long teamId,
            final long canisterId,
            final GasUser user,
            final @Nullable MarkEmptyRequest request) {
        teamAccess.requireMember(teamId, user);
        return canisterOf(
                scrubberDataService.markEmpty(
                        teamId, canisterId, request == null ? null : request.emptiedAt()),
                members(teamId));
    }

    public ScrubberCanister reopen(final long teamId, final long canisterId, final GasUser user) {
        teamAccess.requireAdmin(teamId, user);
        return canisterOf(scrubberDataService.reopen(teamId, canisterId), members(teamId));
    }

    public ScrubberCanister addCostLine(
            final long teamId,
            final long canisterId,
            final GasUser user,
            final CostLineInput input) {
        final var caller = teamAccess.requireMember(teamId, user);
        requirePayerAllowed(caller, user, input);
        return canisterOf(
                scrubberDataService.addCostLine(teamId, canisterId, input), members(teamId));
    }

    public ScrubberCanister deleteCostLine(
            final long teamId, final long canisterId, final long costLineId, final GasUser user) {
        teamAccess.requireAdmin(teamId, user);
        return canisterOf(
                scrubberDataService.deleteCostLine(teamId, canisterId, costLineId),
                members(teamId));
    }

    /** The stored canisters as pricing input (also for the balances, DOMAIN §8). */
    static List<ScrubberPricing.Canister> calcCanisters(final List<StoredCanister> canisters) {
        return canisters.stream().map(ScrubberService::calcCanister).toList();
    }

    private static ScrubberPricing.Canister calcCanister(final StoredCanister canister) {
        return new ScrubberPricing.Canister(
                canister.canisterId(),
                canister.capacityKg(),
                canister.emptiedAt() != null,
                canister.costLines().stream()
                        .map(
                                line ->
                                        new ScrubberPricing.CostLine(
                                                line.payerMemberId(), line.amount()))
                        .toList(),
                canister.uses().stream()
                        .map(use -> new ScrubberPricing.Use(use.useId(), use.memberId(), use.kg()))
                        .toList());
    }

    /** New records need the team to track scrubbers; what exists stays readable and correctable. */
    private TeamMembership requireTracking(final long teamId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        if (!teamDataService.getTeam(caller).scrubberTrackingEnabled()) {
            throw new IllegalStateException("Scrubber tracking is off for this team.");
        }
        return caller;
    }

    /** A cost line for another payer needs an ADMIN, as on a bank (DOMAIN §10). */
    private void requirePayerAllowed(
            final TeamMembership caller, final GasUser user, final CostLineInput line) {
        if (line.payerMemberId() != caller.memberId()) {
            teamAccess.requireAdmin(caller.teamId(), user);
        }
    }

    private Map<Long, Member> members(final long teamId) {
        return teamDataService.members(teamId).stream()
                .collect(Collectors.toMap(Member::memberId, Function.identity()));
    }

    private static ScrubberCanister canisterOf(
            final StoredCanister canister, final Map<Long, Member> members) {
        final var priced = ScrubberPricing.price(calcCanister(canister));
        final boolean empty = canister.emptiedAt() != null;
        return new ScrubberCanister(
                canister.canisterId(),
                canister.label(),
                empty ? CanisterStatus.EMPTY : CanisterStatus.OPEN,
                canister.capacityKg(),
                priced.recordedKg(),
                empty ? 0 : Math.max(0, canister.capacityKg() - priced.recordedKg()),
                Money.amount(priced.totalCost()),
                Money.unitPrice(priced.pricePerKg()),
                priced.isEstimate(),
                canister.openedAt(),
                canister.emptiedAt(),
                ref(canister.createdByMemberId(), members),
                canister.costLines().stream()
                        .map(
                                line ->
                                        new ScrubberCanister.CostLine(
                                                line.costLineId(),
                                                ref(line.payerMemberId(), members),
                                                line.amount(),
                                                line.paidAt(),
                                                line.note()))
                        .toList(),
                canister.uses().stream()
                        .map(
                                use ->
                                        new ScrubberCanister.Use(
                                                use.useId(),
                                                ref(use.memberId(), members),
                                                use.kg(),
                                                Money.amount(
                                                        Objects.requireNonNull(
                                                                priced.chargeByUseId()
                                                                        .get(use.useId()))),
                                                use.usedAt(),
                                                ref(use.recordedByMemberId(), members)))
                        .toList());
    }

    private static MemberRef ref(final long memberId, final Map<Long, Member> members) {
        final var member = Objects.requireNonNull(members.get(memberId));
        return new MemberRef(member.memberId(), member.displayName());
    }
}
