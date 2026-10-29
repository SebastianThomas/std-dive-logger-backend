package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.calc.allocation.TransferGraph;
import ch.sthomas.stddivegastracker.data.repository.FillRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.data.repository.VesselRepository;
import ch.sthomas.stddivegastracker.model.Page;
import ch.sthomas.stddivegastracker.model.entity.FillEntity;
import ch.sthomas.stddivegastracker.model.entity.FillStageEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselEntity;
import ch.sthomas.stddivegastracker.model.entity.VesselReadingEntity;
import ch.sthomas.stddivegastracker.model.fill.FillContent;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StoredFill;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fill aggregate (DOMAIN §5) plus the rows a fill produces elsewhere: readings of its source banks
 * (§6) and the declared mix of a bank it fills (§5.6). Fills are looked up within the team (404).
 */
@Service
public class FillDataService {

    /** The id of a fill that is not stored yet: no stored fill has it. */
    private static final long NEW_FILL = 0;

    private final FillRepository fillRepository;
    private final VesselRepository vesselRepository;
    private final TeamRepository teamRepository;

    public FillDataService(
            final FillRepository fillRepository,
            final VesselRepository vesselRepository,
            final TeamRepository teamRepository) {
        this.fillRepository = fillRepository;
        this.vesselRepository = vesselRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public StoredFill get(final long teamId, final long fillId) {
        return fillRepository
                .findByIdAndTeamId(fillId, teamId)
                .map(FillEntity::toRecord)
                .orElseThrow(() -> new NoSuchElementException("Fill not found."));
    }

    /** Newest first; the stages of the whole page come in one extra query (no N+1). */
    @Transactional(readOnly = true)
    public Page<StoredFill> list(
            final long teamId,
            final @Nullable Long memberId,
            final @Nullable Long vesselId,
            final boolean includeVoided,
            final int page,
            final int size) {
        final var found =
                fillRepository.findPage(
                        teamId, includeVoided, memberId, vesselId, PageRequest.of(page, size));
        if (found.isEmpty()) {
            return new Page<>(
                    List.of(), page, size, found.getTotalElements(), found.getTotalPages());
        }
        final var withStages =
                fillRepository
                        .findWithStages(found.getContent().stream().map(FillEntity::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(FillEntity::getId, Function.identity()));
        return new Page<>(
                found.getContent().stream()
                        .map(
                                fill ->
                                        Objects.requireNonNull(withStages.get(fill.getId()))
                                                .toRecord())
                        .toList(),
                page,
                size,
                found.getTotalElements(),
                found.getTotalPages());
    }

    /** Non-voided cylinder fills per charged member in {@code [from, to)}, member id → count. */
    @Transactional(readOnly = true)
    public Map<Long, Long> cylinderFillCounts(
            final long teamId, final Instant from, final Instant to) {
        return fillRepository.countCylinderFills(teamId, from, to).stream()
                .collect(
                        Collectors.toMap(
                                FillRepository.MemberFillCount::memberId,
                                FillRepository.MemberFillCount::fills));
    }

    /**
     * Also writes the readings and the target bank's mix the fill implies. Under the team lock the
     * banks' service windows ({@link #requireInService}) and, for a fill into a bank, the transfer
     * graph ({@link #requireNoLoop}) are checked again.
     */
    @Transactional
    public StoredFill create(
            final long teamId, final long recordedByMemberId, final FillContent content) {
        lockTeam(teamId);
        requireInService(teamId, content, null);
        requireNoLoop(teamId, content, NEW_FILL);
        final var fill = new FillEntity(teamId, recordedByMemberId);
        apply(fill, content);
        fillRepository.saveAndFlush(fill);
        writeSideEffects(teamId, recordedByMemberId, fill.getId(), content);
        return fill.toRecord();
    }

    /**
     * Replaces the content and the fill's readings; who recorded it stays. The old stages go first
     * (flush), else the unique (fill, stage index) fires.
     */
    @Transactional
    public StoredFill update(
            final long teamId,
            final long fillId,
            final long editorMemberId,
            final FillContent content) {
        lockTeam(teamId);
        final var fill = lock(teamId, fillId);
        if (fill.isVoided()) {
            throw new IllegalStateException("A voided fill can't be changed.");
        }
        requireInService(teamId, content, fill.getTargetVesselId());
        requireNoLoop(teamId, content, fillId);
        vesselRepository.deleteReadingsOfFill(fillId);
        fill.getStages().clear();
        fillRepository.flush();
        apply(fill, content);
        fillRepository.flush();
        writeSideEffects(teamId, editorMemberId, fillId, content);
        return fill.toRecord();
    }

    /**
     * Deletes the fill's readings. A declared mix the fill set on its target bank stays (the
     * analysis was real).
     */
    @Transactional
    public StoredFill voidFill(final long teamId, final long fillId, final String reason) {
        final var fill = lock(teamId, fillId);
        if (fill.isVoided()) {
            throw new IllegalStateException("This fill is already voided.");
        }
        vesselRepository.deleteReadingsOfFill(fillId);
        fill.markVoided(Instant.now(), reason);
        fillRepository.flush();
        return fill.toRecord();
    }

    /**
     * Every fill write takes the team lock first (as membership changes and closing a bank do;
     * always before the fill lock), so the checks below see every committed write and none can slip
     * in between check and save.
     */
    private void lockTeam(final long teamId) {
        if (teamRepository.findForUpdate(teamId).isEmpty()) {
            throw new NoSuchElementException("Team not found.");
        }
    }

    /**
     * DOMAIN §5.6, the guarantee behind the service's check on its snapshot: a bank closed in the
     * meantime. A closed target is only allowed where the update keeps the target it had ({@code
     * keptTargetVesselId}), and then the fill still lies before the closing.
     */
    private void requireInService(
            final long teamId, final FillContent content, final @Nullable Long keptTargetVesselId) {
        final var filledAt = content.filledAt();
        for (final var stage : content.stages()) {
            final var sourceVesselId = stage.sourceVesselId();
            if (sourceVesselId != null && !vessel(teamId, sourceVesselId).isInServiceAt(filledAt)) {
                throw new IllegalArgumentException("That bank was not in service at that time.");
            }
        }
        if (content.target() instanceof FillTarget.Vessel(final var targetVesselId, _)) {
            final var target = vessel(teamId, targetVesselId);
            if (target.isClosed() && !Objects.equals(keptTargetVesselId, targetVesselId)) {
                throw new IllegalStateException("Bank is closed.");
            }
            if (!target.isInServiceAt(filledAt)) {
                throw new IllegalArgumentException("That bank was not in service at that time.");
            }
        }
    }

    /**
     * DOMAIN §5.6, the guarantee behind the service's fast check: two transfers recorded at once
     * could each pass it and close a loop (the engine then throws for the whole team). Runs under
     * the team lock and re-reads the committed transfers, without the fill being replaced.
     */
    private void requireNoLoop(final long teamId, final FillContent content, final long fillId) {
        if (!(content.target() instanceof FillTarget.Vessel(final var targetVesselId, _))) {
            return;
        }
        TransferGraph.requireNoLoop(
                fillRepository.findTransferEdges(teamId, fillId),
                targetVesselId,
                content.stages().stream()
                        .map(FillContent.Stage::sourceVesselId)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private FillEntity lock(final long teamId, final long fillId) {
        return fillRepository
                .findForUpdate(fillId, teamId)
                .orElseThrow(() -> new NoSuchElementException("Fill not found."));
    }

    private static void apply(final FillEntity fill, final FillContent content) {
        fill.setFilledAt(content.filledAt());
        switch (content.target()) {
            case FillTarget.Cylinder(final var volume, final var label, final var memberId) ->
                    fill.setCylinderTarget(volume, label, memberId);
            case FillTarget.Vessel(final var vesselId, _) -> fill.setVesselTarget(vesselId);
        }
        fill.setStartPressureBar(content.startPressureBar());
        fill.setStartMix(content.startMix());
        fill.setEndPressureBar(content.endPressureBar());
        fill.setEndMix(content.endMix());
        fill.setEndAnalysis(content.endAnalysis());
        fill.setNotes(content.notes());
        fill.setCalcModelVersion(content.calcModelVersion());
        for (final var stage : content.stages()) {
            fill.getStages()
                    .add(
                            new FillStageEntity(
                                    stage.stageIndex(),
                                    stage.sourceVesselId(),
                                    stage.sourceMix(),
                                    stage.pressureAfterBar(),
                                    stage.addedLitres(),
                                    stage.addedO2Litres(),
                                    stage.addedHeLitres(),
                                    stage.lineLossLitres(),
                                    stage.drawLitres(),
                                    stage.sourcePressureAfterBar()));
        }
    }

    /** Readings at the fill's time on the source banks; the analysis becomes the target's mix. */
    private void writeSideEffects(
            final long teamId, final long memberId, final long fillId, final FillContent content) {
        for (final var stage : content.stages()) {
            final var sourceVesselId = stage.sourceVesselId();
            final var pressureBar = stage.sourcePressureAfterBar();
            if (sourceVesselId != null && pressureBar != null) {
                vessel(teamId, sourceVesselId)
                        .getReadings()
                        .add(
                                new VesselReadingEntity(
                                        pressureBar, content.filledAt(), fillId, memberId));
            }
        }
        final var analysis = content.endAnalysis();
        if (analysis != null && content.target() instanceof FillTarget.Vessel(final var id, _)) {
            vessel(teamId, id).setMix(analysis);
        }
    }

    private VesselEntity vessel(final long teamId, final long vesselId) {
        return vesselRepository
                .findByIdAndTeamId(vesselId, teamId)
                .orElseThrow(() -> new NoSuchElementException("Bank not found."));
    }
}
