package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.FillRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.data.repository.VesselRepository;
import ch.sthomas.stddivegastracker.model.entity.VesselCostLineEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselReadingEntity;
import ch.sthomas.stddivegastracker.model.team.TeamFacts;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.vessel.CloseVesselRequest;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.model.vessel.ReadingInput;
import ch.sthomas.stddivegastracker.model.vessel.VesselCreate;
import ch.sthomas.stddivegastracker.model.vessel.VesselUpdate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Vessel aggregate (DOMAIN §2, §6, §7.3) plus the flat read of a team's facts. Vessels are looked
 * up within the team, so another team's id is a 404; who may call is the service's business.
 */
@Service
public class VesselDataService {

    private final VesselRepository vesselRepository;
    private final FillRepository fillRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamDataService teamDataService;

    public VesselDataService(
            final VesselRepository vesselRepository,
            final FillRepository fillRepository,
            final TeamRepository teamRepository,
            final TeamMemberRepository memberRepository,
            final TeamDataService teamDataService) {
        this.vesselRepository = vesselRepository;
        this.fillRepository = fillRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.teamDataService = teamDataService;
    }

    /**
     * Everything the snapshot needs in a handful of flat queries (never per vessel). REPEATABLE
     * READ, so a fill committed meanwhile cannot show up in one query and not in another.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeamFacts teamFacts(final long teamId) {
        return new TeamFacts(
                vesselRepository.findRows(teamId),
                vesselRepository.findCostLineRows(teamId),
                vesselRepository.findReadingRows(teamId),
                vesselRepository.findParticipantRows(teamId),
                fillRepository.findGasStageRows(teamId),
                teamDataService.members(teamId));
    }

    /** The member who added the vessel (for the creator-may-close rule, DOMAIN §10). */
    @Transactional(readOnly = true)
    public long creatorOf(final long teamId, final long vesselId) {
        return find(teamId, vesselId).getCreatedByMemberId();
    }

    /** Defaults come from the team now: loss policy and residual pressure are snapshots. */
    @Transactional
    public long create(final TeamMembership caller, final VesselCreate request) {
        final var team =
                teamRepository
                        .findById(caller.teamId())
                        .orElseThrow(() -> new NoSuchElementException("Team not found."));
        if (!team.isGasTrackingEnabled()) {
            throw new IllegalStateException("Fill tracking is off for this team.");
        }
        final var vessel =
                new VesselEntity(
                        caller.teamId(),
                        caller.memberId(),
                        request.label(),
                        request.waterVolumeLitres(),
                        request.mix(),
                        Objects.requireNonNullElse(request.initialPressureBar(), 0.0),
                        Objects.requireNonNullElse(
                                request.expectedResidualPressureBar(),
                                team.getDefaultResidualPressureBar()),
                        Objects.requireNonNullElse(
                                request.lossPolicy(), team.getDefaultLossPolicy()),
                        Objects.requireNonNullElseGet(request.openedAt(), Instant::now));
        vessel.setNotes(request.notes());
        for (final var costLine : request.initialCostLines()) {
            vessel.getCostLines().add(newCostLine(caller.teamId(), costLine));
        }
        return vesselRepository.save(vessel).getId();
    }

    /** Absent fields stay; the loss policy only changes while IN_SERVICE (DOMAIN §7.3). */
    @Transactional
    public void update(final long teamId, final long vesselId, final VesselUpdate request) {
        final var vessel = find(teamId, vesselId);
        final var label = request.label();
        if (label != null) {
            vessel.setLabel(label);
        }
        final var notes = request.notes();
        if (notes != null) {
            vessel.setNotes(notes.isEmpty() ? null : notes);
        }
        final var residualPressureBar = request.expectedResidualPressureBar();
        if (residualPressureBar != null) {
            vessel.setExpectedResidualPressureBar(residualPressureBar);
        }
        final var mix = request.mix();
        if (mix != null) {
            vessel.setMix(mix);
            // a correction of the registered gas, unless gas was blended in since (DOMAIN §6)
            if (!fillRepository.existsByTeamIdAndTargetVesselIdAndVoidedAtIsNull(
                    teamId, vesselId)) {
                vessel.setInitialMix(mix);
            }
        }
        final var lossPolicy = request.lossPolicy();
        if (lossPolicy != null) {
            if (vessel.isClosed()) {
                throw new IllegalStateException(
                        "The loss policy can only change while the bank is in service.");
            }
            vessel.setLossPolicy(lossPolicy);
        }
    }

    /**
     * Writes the closing participants (DOMAIN §7.3): members of the team who are not LEFT (ACTIVE
     * or PENDING) and had joined by the closing time. The engine applies the fallbacks. The closing
     * time lies after the opening and after every fill and reading of the bank, so they all stay
     * inside its service window (DOMAIN §5.6, §6).
     */
    @Transactional
    public void close(final long teamId, final long vesselId, final CloseVesselRequest request) {
        // the team lock fill writes take: no fill can land after the closing while it commits
        if (teamRepository.findForUpdate(teamId).isEmpty()) {
            throw new NoSuchElementException("Team not found.");
        }
        final var vessel = find(teamId, vesselId);
        if (vessel.isClosed()) {
            throw new IllegalStateException("This bank is already closed.");
        }
        final var closedAt = Objects.requireNonNullElseGet(request.closedAt(), Instant::now);
        if (!closedAt.isAfter(vessel.getOpenedAt())) {
            throw new IllegalArgumentException("A bank can only be closed after it was opened.");
        }
        final var lastUse =
                Stream.concat(
                                fillRepository.findLatestFillOf(teamId, vesselId).stream(),
                                vessel.getReadings().stream()
                                        .map(VesselReadingEntity::getMeasuredAt))
                        .max(Comparator.naturalOrder());
        if (lastUse.isPresent() && !closedAt.isAfter(lastUse.get())) {
            throw new IllegalArgumentException(
                    "A bank can only be closed after its last fill and reading.");
        }
        vessel.close(closedAt, request.closingPressureBar());
        for (final var member : memberRepository.findByTeamId(teamId)) {
            if (member.getLeftAt() == null && !member.getJoinedAt().isAfter(closedAt)) {
                vessel.getLossParticipantMemberIds().add(member.getId());
            }
        }
    }

    @Transactional
    public void reopen(final long teamId, final long vesselId) {
        final var vessel = find(teamId, vesselId);
        if (!vessel.isClosed()) {
            throw new IllegalStateException("This bank is not closed.");
        }
        vessel.reopen();
    }

    @Transactional
    public void addCostLine(final long teamId, final long vesselId, final CostLineInput input) {
        find(teamId, vesselId).getCostLines().add(newCostLine(teamId, input));
    }

    @Transactional
    public void deleteCostLine(final long teamId, final long vesselId, final long costLineId) {
        final var removed =
                find(teamId, vesselId).getCostLines().removeIf(line -> line.getId() == costLineId);
        if (!removed) {
            throw new NoSuchElementException("Cost line not found.");
        }
    }

    /** A reading lies in the bank's service window, like a fill from it (DOMAIN §5.6). */
    @Transactional
    public void addReading(
            final long teamId, final long vesselId, final long memberId, final ReadingInput input) {
        final var vessel = find(teamId, vesselId);
        final var measuredAt = Objects.requireNonNullElseGet(input.measuredAt(), Instant::now);
        if (!vessel.isInServiceAt(measuredAt)) {
            throw new IllegalArgumentException(
                    vessel.isClosed() && !measuredAt.isBefore(vessel.getOpenedAt())
                            ? "This bank is closed."
                            : "That bank was not in service at that time.");
        }
        vessel.getReadings()
                .add(new VesselReadingEntity(input.pressureBar(), measuredAt, null, memberId));
    }

    private VesselEntity find(final long teamId, final long vesselId) {
        return vesselRepository
                .findByIdAndTeamId(vesselId, teamId)
                .orElseThrow(() -> new NoSuchElementException("Bank not found."));
    }

    /** The payer must belong to this team and not be LEFT; PENDING is fine (DOMAIN §2). */
    private VesselCostLineEntity newCostLine(final long teamId, final CostLineInput input) {
        final var payer =
                memberRepository
                        .findByIdAndTeamId(input.payerMemberId(), teamId)
                        .filter(member -> member.getLeftAt() == null)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown payer."));
        return new VesselCostLineEntity(
                payer.getId(),
                input.amount(),
                Objects.requireNonNullElseGet(input.paidAt(), Instant::now),
                input.note());
    }
}
