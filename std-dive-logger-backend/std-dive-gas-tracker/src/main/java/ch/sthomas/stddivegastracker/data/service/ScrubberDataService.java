package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.ScrubberCanisterRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.model.entity.ScrubberCanisterEntity;
import ch.sthomas.stddivegastracker.model.entity.ScrubberCostLineEntity;
import ch.sthomas.stddivegastracker.model.entity.ScrubberUseEntity;
import ch.sthomas.stddivegastracker.model.scrubber.CanisterCreate;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberUseInput;
import ch.sthomas.stddivegastracker.model.scrubber.StoredCanister;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Canister aggregate (DOMAIN §12). Canisters are looked up within the team, so another team's id is
 * a 404; who may call is the service's business. Writes take the team lock, as fills do, so a use
 * cannot land after the canister was marked empty.
 */
@Service
public class ScrubberDataService {

    private final ScrubberCanisterRepository canisterRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    public ScrubberDataService(
            final ScrubberCanisterRepository canisterRepository,
            final TeamRepository teamRepository,
            final TeamMemberRepository memberRepository) {
        this.canisterRepository = canisterRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<StoredCanister> list(final long teamId) {
        return canisterRepository.findByTeamIdOrderByOpenedAtDescIdDesc(teamId).stream()
                .map(ScrubberCanisterEntity::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredCanister get(final long teamId, final long canisterId) {
        return find(teamId, canisterId).toRecord();
    }

    @Transactional
    public StoredCanister create(final TeamMembership caller, final CanisterCreate request) {
        lockTeam(caller.teamId());
        final var canister =
                new ScrubberCanisterEntity(
                        caller.teamId(),
                        request.label(),
                        Objects.requireNonNull(request.capacityKg()),
                        Objects.requireNonNullElseGet(request.openedAt(), Instant::now),
                        caller.memberId());
        for (final var line : request.initialCostLines()) {
            canister.getCostLines().add(newCostLine(caller.teamId(), line));
        }
        return canisterRepository.saveAndFlush(canister).toRecord();
    }

    /** A use lies in the canister's window and is for a member of the team who has not left. */
    @Transactional
    public StoredCanister addUse(
            final TeamMembership caller, final long canisterId, final ScrubberUseInput input) {
        lockTeam(caller.teamId());
        final var canister = find(caller.teamId(), canisterId);
        final var usedAt = Objects.requireNonNullElseGet(input.usedAt(), Instant::now);
        if (!canister.isInUseAt(usedAt)) {
            throw new IllegalArgumentException(
                    canister.isEmptied() && !usedAt.isBefore(canister.getOpenedAt())
                            ? "This canister is empty."
                            : "That canister was not in use at that time.");
        }
        final var member =
                memberRepository
                        .findByIdAndTeamId(input.memberId(), caller.teamId())
                        .filter(found -> found.getLeftAt() == null)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown member."));
        canister.getUses()
                .add(new ScrubberUseEntity(member.getId(), input.kg(), usedAt, caller.memberId()));
        return flushed(canister);
    }

    @Transactional
    public StoredCanister deleteUse(final long teamId, final long canisterId, final long useId) {
        lockTeam(teamId);
        final var canister = find(teamId, canisterId);
        if (!canister.getUses().removeIf(use -> use.getId() == useId)) {
            throw new NoSuchElementException("Use not found.");
        }
        return flushed(canister);
    }

    /** "It is empty now": after its opening and its last use, so they all stay in its window. */
    @Transactional
    public StoredCanister markEmpty(
            final long teamId, final long canisterId, final @Nullable Instant emptiedAt) {
        lockTeam(teamId);
        final var canister = find(teamId, canisterId);
        if (canister.isEmptied()) {
            throw new IllegalStateException("This canister is already empty.");
        }
        final var at = Objects.requireNonNullElseGet(emptiedAt, Instant::now);
        final var lastUse =
                canister.getUses().stream()
                        .map(ScrubberUseEntity::getUsedAt)
                        .max(Comparator.naturalOrder());
        if (!at.isAfter(canister.getOpenedAt())
                || (lastUse.isPresent() && !at.isAfter(lastUse.get()))) {
            throw new IllegalArgumentException(
                    "A canister can only be empty after its opening and its last use.");
        }
        canister.markEmptied(at);
        return flushed(canister);
    }

    @Transactional
    public StoredCanister reopen(final long teamId, final long canisterId) {
        lockTeam(teamId);
        final var canister = find(teamId, canisterId);
        if (!canister.isEmptied()) {
            throw new IllegalStateException("This canister is not empty.");
        }
        canister.markEmptied(null);
        return flushed(canister);
    }

    @Transactional
    public StoredCanister addCostLine(
            final long teamId, final long canisterId, final CostLineInput input) {
        lockTeam(teamId);
        final var canister = find(teamId, canisterId);
        canister.getCostLines().add(newCostLine(teamId, input));
        return flushed(canister);
    }

    @Transactional
    public StoredCanister deleteCostLine(
            final long teamId, final long canisterId, final long costLineId) {
        lockTeam(teamId);
        final var canister = find(teamId, canisterId);
        if (!canister.getCostLines().removeIf(line -> line.getId() == costLineId)) {
            throw new NoSuchElementException("Cost line not found.");
        }
        return flushed(canister);
    }

    /** Ids of new children exist only after the flush, and the record carries them. */
    private StoredCanister flushed(final ScrubberCanisterEntity canister) {
        canisterRepository.saveAndFlush(canister);
        return canister.toRecord();
    }

    private void lockTeam(final long teamId) {
        if (teamRepository.findForUpdate(teamId).isEmpty()) {
            throw new NoSuchElementException("Team not found.");
        }
    }

    private ScrubberCanisterEntity find(final long teamId, final long canisterId) {
        return canisterRepository
                .findByIdAndTeamId(canisterId, teamId)
                .orElseThrow(() -> new NoSuchElementException("Canister not found."));
    }

    /** The payer must belong to this team and not be LEFT, as on a bank. */
    private ScrubberCostLineEntity newCostLine(final long teamId, final CostLineInput input) {
        final var payer =
                memberRepository
                        .findByIdAndTeamId(input.payerMemberId(), teamId)
                        .filter(member -> member.getLeftAt() == null)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown payer."));
        return new ScrubberCostLineEntity(
                payer.getId(),
                input.amount(),
                Objects.requireNonNullElseGet(input.paidAt(), Instant::now),
                input.note());
    }
}
