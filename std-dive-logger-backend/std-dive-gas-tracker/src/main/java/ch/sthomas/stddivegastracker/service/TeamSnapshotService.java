package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationEngine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Event;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Inflow;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Outflow;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.Reading;
import ch.sthomas.stddivegastracker.data.service.VesselDataService;
import ch.sthomas.stddivegastracker.model.fill.FillContent;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.TeamFacts;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.CostLineRow;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.ParticipantRow;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.StageRow;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns a team's stored facts into calc inputs (DOMAIN §6, §7): replay per vessel, then the engine
 * over all vessels and draws, with {@link VirialFit}. The caller's membership proves access.
 */
@Service
public final class TeamSnapshotService {

    private final VesselDataService vesselDataService;

    public TeamSnapshotService(final VesselDataService vesselDataService) {
        this.vesselDataService = vesselDataService;
    }

    /** Facts plus derived state; {@code members}: everyone incl. LEFT, by member id. */
    public record TeamState(
            TeamFacts facts,
            AllocationInput input,
            AllocationResult allocation,
            Map<Long, InventoryReplay.Result> inventory,
            Map<Long, MemberRef> members) {

        public TeamState {
            inventory = Map.copyOf(inventory);
            members = Map.copyOf(members);
        }

        public InventoryReplay.Result inventoryOf(final long vesselId) {
            return Objects.requireNonNull(inventory.get(vesselId), "No vessel " + vesselId);
        }

        public MemberRef member(final long memberId) {
            return Objects.requireNonNull(members.get(memberId), "No member " + memberId);
        }
    }

    public TeamState snapshot(final TeamMembership caller) {
        return compute(vesselDataService.teamFacts(caller.teamId()));
    }

    /**
     * The state as if {@code content} were stored as fill {@code fillId} (0 = new, else it replaces
     * that fill's stages): the in-memory what-if behind preview and pricing before a write. Its
     * stages carry the ids of {@code content}.
     */
    public TeamState withFill(final TeamState state, final long fillId, final FillContent content) {
        final var facts = state.facts();
        final var stages =
                new ArrayList<>(
                        facts.stages().stream().filter(row -> row.fillId() != fillId).toList());
        final var target = content.target();
        final var targetVesselId = target instanceof FillTarget.Vessel(final var id, _) ? id : null;
        final var chargedMemberId = FillTarget.chargedMemberOf(target);
        for (final var stage : content.stages()) {
            stages.add(
                    new StageRow(
                            stage.stageId(),
                            fillId,
                            content.filledAt(),
                            targetVesselId,
                            chargedMemberId,
                            stage.sourceVesselId(),
                            stage.sourceMix().o2(),
                            stage.sourceMix().he(),
                            stage.addedLitres(),
                            stage.addedO2Litres(),
                            stage.addedHeLitres(),
                            stage.lineLossLitres(),
                            stage.drawLitres()));
        }
        return compute(
                new TeamFacts(
                        facts.vessels(),
                        facts.costLines(),
                        facts.readings(),
                        facts.participants(),
                        stages,
                        facts.members()));
    }

    private static TeamState compute(final TeamFacts facts) {
        final var eventsByVessel = new HashMap<Long, List<Event>>();
        for (final var reading : facts.readings()) {
            eventsByVessel
                    .computeIfAbsent(reading.vesselId(), _ -> new ArrayList<>())
                    .add(
                            new Reading(
                                    reading.readingId(),
                                    reading.measuredAt(),
                                    reading.pressureBar()));
        }

        // Every stage of a fill into a vessel adds gas to it (AIR too); a stage from a vessel is a
        // draw.
        final var draws = new ArrayList<Draw>();
        final var stagesByFill = new TreeMap<Long, List<StageRow>>();
        for (final var stage : facts.stages()) {
            final var sourceVesselId = stage.sourceVesselId();
            if (sourceVesselId != null) {
                eventsByVessel
                        .computeIfAbsent(sourceVesselId, _ -> new ArrayList<>())
                        .add(
                                new Outflow(
                                        stage.stageId(),
                                        stage.filledAt(),
                                        stage.drawLitres(),
                                        stage.lineLossLitres(),
                                        stage.sourceMix()));
                draws.add(
                        new Draw(
                                stage.stageId(),
                                sourceVesselId,
                                stage.drawLitres(),
                                beneficiary(stage)));
            }
            if (stage.targetVesselId() != null) {
                stagesByFill.computeIfAbsent(stage.fillId(), _ -> new ArrayList<>()).add(stage);
            }
        }
        for (final var fillStages : stagesByFill.values()) {
            final var first = fillStages.getFirst();
            eventsByVessel
                    .computeIfAbsent(
                            Objects.requireNonNull(first.targetVesselId()), _ -> new ArrayList<>())
                    .add(inflow(fillStages));
        }

        final var costLinesByVessel =
                facts.costLines().stream().collect(Collectors.groupingBy(CostLineRow::vesselId));
        final var participantsByVessel =
                facts.participants().stream()
                        .collect(
                                Collectors.groupingBy(
                                        ParticipantRow::vesselId,
                                        Collectors.mapping(
                                                ParticipantRow::memberId, Collectors.toList())));
        final var vessels = new ArrayList<AllocationInput.Vessel>();
        final var inventory = new HashMap<Long, InventoryReplay.Result>();
        for (final var row : facts.vessels()) {
            final var replayed =
                    InventoryReplay.replay(
                            new InventoryReplay.Vessel(
                                    row.waterVolumeLitres(),
                                    row.mix(),
                                    row.initialMix(),
                                    row.initialPressureBar(),
                                    row.expectedResidualPressureBar(),
                                    row.closingPressureBar()),
                            eventsByVessel.getOrDefault(row.vesselId(), List.of()),
                            VirialFit.INSTANCE);
            inventory.put(row.vesselId(), replayed);
            vessels.add(
                    new AllocationInput.Vessel(
                            row.vesselId(),
                            row.lossPolicy(),
                            row.closedAt() == null ? VesselStatus.IN_SERVICE : VesselStatus.CLOSED,
                            costLinesByVessel.getOrDefault(row.vesselId(), List.of()).stream()
                                    .map(line -> new CostLine(line.payerMemberId(), line.amount()))
                                    .toList(),
                            replayed.inflowLitres(),
                            replayed.residualLitres(),
                            participantsByVessel.getOrDefault(row.vesselId(), List.of())));
        }

        final var activeMemberIds =
                facts.members().stream()
                        .filter(member -> member.status() == MemberStatus.ACTIVE)
                        .map(Member::memberId)
                        .collect(Collectors.toSet());
        final var input = new AllocationInput(vessels, draws, activeMemberIds);
        return new TeamState(
                facts,
                input,
                AllocationEngine.allocate(input),
                inventory,
                facts.members().stream()
                        .collect(
                                Collectors.toMap(
                                        Member::memberId,
                                        member ->
                                                new MemberRef(
                                                        member.memberId(), member.displayName()))));
    }

    /** One fill into a vessel: the sum of its stages, components from the stored added litres. */
    private static Inflow inflow(final List<StageRow> fillStages) {
        double o2Litres = 0;
        double heLitres = 0;
        double totalLitres = 0;
        for (final var stage : fillStages) {
            o2Litres += stage.addedO2Litres();
            heLitres += stage.addedHeLitres();
            totalLitres += stage.addedLitres();
        }
        final var first = fillStages.getFirst();
        return new Inflow(first.fillId(), first.filledAt(), o2Litres, heLitres, totalLitres);
    }

    /** A cylinder fill charges its member, a fill into a vessel is an internal transfer. */
    private static Beneficiary beneficiary(final StageRow stage) {
        final var chargedMemberId = stage.chargedMemberId();
        if (chargedMemberId != null) {
            return new ToMember(chargedMemberId);
        }
        return new ToVessel(Objects.requireNonNull(stage.targetVesselId()));
    }
}
