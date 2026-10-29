package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.DrawCharge;
import ch.sthomas.stddivegastracker.calc.allocation.TransferGraph;
import ch.sthomas.stddivegastracker.calc.fill.FillSolver;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.data.service.FillDataService;
import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.model.Money;
import ch.sthomas.stddivegastracker.model.Page;
import ch.sthomas.stddivegastracker.model.fill.Fill;
import ch.sthomas.stddivegastracker.model.fill.FillContent;
import ch.sthomas.stddivegastracker.model.fill.FillInput;
import ch.sthomas.stddivegastracker.model.fill.FillPreview;
import ch.sthomas.stddivegastracker.model.fill.FillStatus;
import ch.sthomas.stddivegastracker.model.fill.FillSummary;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StageResult;
import ch.sthomas.stddivegastracker.model.fill.StageSource;
import ch.sthomas.stddivegastracker.model.fill.StoredFill;
import ch.sthomas.stddivegastracker.model.fill.VoidFillRequest;
import ch.sthomas.stddivegastracker.model.fill.Warning;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.VesselRow;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.TeamSnapshotService.TeamState;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * API.md §4; authorization per DOMAIN §10. Preview, create and update share {@link #draft}: solve,
 * price with the fill applied in memory, and - create/update - store. Money is rounded here (§3).
 */
@Service
public final class FillService {

    private static final int MAX_PAGE_SIZE = 200;

    /** The id of a fill that is not stored yet: no stored fill has it. */
    private static final long NEW_FILL = 0;

    private static final StagePrice FREE = new StagePrice(BigDecimal.ZERO, false);

    private final TeamAccessService teamAccess;
    private final TeamDataService teamDataService;
    private final FillDataService fillDataService;
    private final TeamSnapshotService snapshots;

    public FillService(
            final TeamAccessService teamAccess,
            final TeamDataService teamDataService,
            final FillDataService fillDataService,
            final TeamSnapshotService snapshots) {
        this.teamAccess = teamAccess;
        this.teamDataService = teamDataService;
        this.fillDataService = fillDataService;
        this.snapshots = snapshots;
    }

    /** What a stage costs: the unrounded charge of its draw and whether the price can change. */
    private record StagePrice(BigDecimal charge, boolean isEstimate) {}

    /** A team state with its draw charges indexed by draw id (DOMAIN §7.2). */
    private record Prices(TeamState state, Map<Long, BigDecimal> chargeByDrawId) {

        Prices(final TeamState state) {
            this(
                    state,
                    state.allocation().drawCharges().stream()
                            .collect(Collectors.toMap(DrawCharge::drawId, DrawCharge::charge)));
        }

        /**
         * AIR is free and a voided fill costs nothing (it is not in the state). A draw missing from
         * the state (a fill stored while this request ran) counts as 0 for this response.
         */
        List<StagePrice> of(final FillContent content, final boolean voided) {
            final var prices = new ArrayList<StagePrice>();
            for (final var stage : content.stages()) {
                final var sourceVesselId = stage.sourceVesselId();
                prices.add(
                        voided || sourceVesselId == null
                                ? FREE
                                : new StagePrice(
                                        chargeByDrawId.getOrDefault(
                                                stage.stageId(), BigDecimal.ZERO),
                                        state.allocation().vessel(sourceVesselId).provisional()));
            }
            return prices;
        }
    }

    /** A solved fill, ready to store; {@code state} is the team before it (for names). */
    private record Draft(FillContent content, FillPreview preview, TeamState state) {}

    private record ResolvedTarget(
            FillTarget stored, double waterVolumeLitres, GasMix defaultStart) {}

    private record ResolvedSource(@Nullable Long vesselId, GasMix mix) {}

    /** Newest first. {@code memberId}: the charged member; {@code vesselId}: source or target. */
    public Page<FillSummary> list(
            final long teamId,
            final GasUser user,
            final @Nullable Long memberId,
            final @Nullable Long vesselId,
            final boolean includeVoided,
            final int page,
            final int size) {
        final var caller = teamAccess.requireMember(teamId, user);
        if (page < 0) {
            throw new IllegalArgumentException("Page must be 0 or more.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        // The page first: every fill on it is then in the (later) snapshot, bar a concurrent void.
        final var fills =
                fillDataService.list(teamId, memberId, vesselId, includeVoided, page, size);
        final var prices = new Prices(snapshots.snapshot(caller));
        return fills.map(fill -> summary(fill, prices));
    }

    public Fill get(final long teamId, final long fillId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        return fillOf(caller, fillDataService.get(teamId, fillId));
    }

    /**
     * Computes and prices, stores nothing. {@code replacingFillId}: preview an edit of that fill,
     * so its current stages are left out and the numbers equal what {@link #update} would store.
     */
    public FillPreview preview(
            final long teamId,
            final GasUser user,
            final @Nullable Long replacingFillId,
            final FillInput input) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var previous =
                replacingFillId == null ? null : fillDataService.get(teamId, replacingFillId);
        return draft(caller, input, previous).preview();
    }

    public Fill create(final long teamId, final GasUser user, final FillInput input) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var draft = draft(caller, input, null);
        final var stored = fillDataService.create(teamId, caller.memberId(), draft.content());
        return fillOf(stored, draft.preview(), draft.state());
    }

    /** Replaces the whole fill (stages and readings included); who recorded it stays. */
    public Fill update(
            final long teamId, final long fillId, final GasUser user, final FillInput input) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var previous = fillDataService.get(teamId, fillId);
        requireMayChange(caller, previous);
        if (previous.isVoided()) {
            throw new IllegalStateException("A voided fill can't be changed.");
        }
        final var draft = draft(caller, input, previous);
        final var stored =
                fillDataService.update(teamId, fillId, caller.memberId(), draft.content());
        return fillOf(stored, draft.preview(), draft.state());
    }

    public Fill voidFill(
            final long teamId,
            final long fillId,
            final GasUser user,
            final VoidFillRequest request) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var previous = fillDataService.get(teamId, fillId);
        requireMayChange(caller, previous);
        if (previous.isVoided()) {
            throw new IllegalStateException("This fill is already voided.");
        }
        return fillOf(caller, fillDataService.voidFill(teamId, fillId, request.reason()));
    }

    // --- validate -> resolve -> solve -> price ---------------------------------------------

    /**
     * {@code previous}: the stored fill an update replaces (its mix snapshots and draws count, its
     * time is kept when the input has none), else null.
     */
    private Draft draft(
            final TeamMembership caller,
            final FillInput input,
            final @Nullable StoredFill previous) {
        final var state = snapshots.snapshot(caller);
        final var team = teamDataService.getTeam(caller);
        final long fillId = previous == null ? NEW_FILL : previous.fillId();
        final var filledAt = filledAt(input, previous);
        final var target = resolveTarget(input.target(), filledAt, state, previous);

        final var sources = new ArrayList<ResolvedSource>();
        final var solverStages = new ArrayList<FillSolver.StageInput>();
        for (int i = 0; i < input.stages().size(); i++) {
            final var stage = input.stages().get(i);
            final var source = resolveSource(stage.source(), i, filledAt, state, previous);
            sources.add(source);
            solverStages.add(
                    new FillSolver.StageInput(
                            source.mix(), stage.pressureAfterBar(), source.vesselId() != null));
        }
        if (target.stored() instanceof FillTarget.Vessel(final var targetVesselId, _)) {
            requireNoLoop(
                    state,
                    targetVesselId,
                    sources.stream()
                            .map(ResolvedSource::vesselId)
                            .filter(Objects::nonNull)
                            .toList(),
                    fillId);
        }

        final var startMix = Objects.requireNonNullElse(input.startMix(), target.defaultStart());
        final var solved =
                FillSolver.solve(
                        new FillSolver.Input(
                                target.waterVolumeLitres(),
                                input.startPressureBar(),
                                startMix,
                                solverStages,
                                input.endAnalysis()),
                        VirialFit.INSTANCE,
                        team.lineDeadVolumeLitres());

        final var stages = new ArrayList<FillContent.Stage>();
        for (int i = 0; i < solved.stages().size(); i++) {
            final var result = solved.stages().get(i);
            final var stage = input.stages().get(i);
            stages.add(
                    new FillContent.Stage(
                            -(i + 1L),
                            i,
                            sources.get(i).vesselId(),
                            sources.get(i).mix(),
                            stage.pressureAfterBar(),
                            result.addedLitres(),
                            result.addedO2Litres(),
                            result.addedHeLitres(),
                            result.lineLossLitres(),
                            result.drawLitres(),
                            sources.get(i).vesselId() == null
                                    ? null
                                    : stage.sourcePressureAfterBar()));
        }
        final var content =
                new FillContent(
                        filledAt,
                        target.stored(),
                        input.startPressureBar(),
                        startMix,
                        solved.endPressureBar(),
                        solved.endMix(),
                        input.endAnalysis(),
                        input.notes(),
                        VirialFit.MODEL_VERSION,
                        stages);

        final var prices =
                new Prices(snapshots.withFill(state, fillId, content)).of(content, false);
        final var warnings = new ArrayList<>(analysisWarnings(content));
        warnings.addAll(overdrawWarnings(content, state, previous));
        return new Draft(content, previewOf(content, prices, warnings, state), state);
    }

    private static Instant filledAt(final FillInput input, final @Nullable StoredFill previous) {
        final var given = input.filledAt();
        if (given != null) {
            return given;
        }
        return previous == null ? Instant.now() : previous.content().filledAt();
    }

    /**
     * A cylinder is charged to a member of the team who has not left (PENDING is fine). A bank
     * target is in service now and was open at {@code filledAt} (DOMAIN §5.6). An update may keep
     * the target it had - a member who left since, a bank closed since (then {@code filledAt} stays
     * before the closing) - so a wrong pressure can still be corrected.
     */
    private static ResolvedTarget resolveTarget(
            final FillTarget target,
            final Instant filledAt,
            final TeamState state,
            final @Nullable StoredFill previous) {
        final var before = previous == null ? null : previous.content().target();
        return switch (target) {
            case FillTarget.Cylinder(final var volume, _, final var memberId) -> {
                final var kept =
                        before instanceof FillTarget.Cylinder(_, _, final var previousMemberId)
                                && previousMemberId == memberId;
                final var chargeable =
                        state.facts().members().stream()
                                .anyMatch(
                                        member ->
                                                member.memberId() == memberId
                                                        && (kept
                                                                || member.status()
                                                                        != MemberStatus.LEFT));
                if (!chargeable) {
                    throw new IllegalArgumentException("Unknown member.");
                }
                yield new ResolvedTarget(target, volume, GasMix.AIR);
            }
            case FillTarget.Vessel(final var vesselId, _) -> {
                final var bank = bank(state, vesselId);
                final var kept =
                        before instanceof FillTarget.Vessel(final var previousVesselId, _)
                                && previousVesselId == vesselId;
                final var closedAt = bank.closedAt();
                if (closedAt != null && !kept) {
                    throw new IllegalStateException("Bank is closed.");
                }
                if (bank.openedAt().isAfter(filledAt)
                        || (closedAt != null && !filledAt.isBefore(closedAt))) {
                    throw new IllegalArgumentException(
                            "That bank was not in service at that time.");
                }
                yield new ResolvedTarget(
                        new FillTarget.Vessel(vesselId, null),
                        bank.waterVolumeLitres(),
                        bank.mix());
            }
        };
    }

    /**
     * A bank source must be in service at {@code filledAt} (DOMAIN §5.6). Its mix is the declared
     * one, except that an update keeps a stage's snapshot while its source stays (DOMAIN §5.1).
     */
    private static ResolvedSource resolveSource(
            final StageSource source,
            final int index,
            final Instant filledAt,
            final TeamState state,
            final @Nullable StoredFill previous) {
        return switch (source) {
            case StageSource.Air _ -> new ResolvedSource(null, GasMix.AIR);
            case StageSource.Vessel(final var vesselId, _) -> {
                final var bank = bank(state, vesselId);
                final var closedAt = bank.closedAt();
                if (bank.openedAt().isAfter(filledAt)
                        || (closedAt != null && !filledAt.isBefore(closedAt))) {
                    throw new IllegalArgumentException(
                            "That bank was not in service at that time.");
                }
                yield new ResolvedSource(vesselId, keptOrDeclaredMix(bank, index, previous));
            }
        };
    }

    private static GasMix keptOrDeclaredMix(
            final VesselRow bank, final int index, final @Nullable StoredFill previous) {
        if (previous != null && index < previous.content().stages().size()) {
            final var old = previous.content().stages().get(index);
            if (Objects.equals(old.sourceVesselId(), bank.vesselId())) {
                return old.sourceMix();
            }
        }
        return bank.mix();
    }

    /**
     * Fast 400 from the snapshot (DOMAIN §5.6); the replaced fill's own transfers do not count. The
     * guarantee is the locked re-check in {@code FillDataService}.
     */
    private static void requireNoLoop(
            final TeamState state,
            final long targetVesselId,
            final Collection<Long> sourceVesselIds,
            final long replacedFillId) {
        final var edges = new ArrayList<TransferGraph.Edge>();
        for (final var row : state.facts().stages()) {
            final var from = row.sourceVesselId();
            final var to = row.targetVesselId();
            if (from != null && to != null && row.fillId() != replacedFillId) {
                edges.add(new TransferGraph.Edge(from, to));
            }
        }
        TransferGraph.requireNoLoop(edges, targetVesselId, sourceVesselIds);
    }

    // --- warnings ---------------------------------------------------------------------------

    /** DOMAIN §5.2, also recomputed on every GET from the stored end mix. */
    private static List<Warning> analysisWarnings(final FillContent content) {
        final var analysis = content.endAnalysis();
        if (analysis == null || !FillSolver.analysisMismatch(content.endMix(), analysis)) {
            return List.of();
        }
        return List.of(
                new Warning(
                        Warning.Code.ANALYSIS_MISMATCH,
                        "The analysed mix "
                                + mixLabel(analysis)
                                + " differs from the calculated "
                                + mixLabel(content.endMix())
                                + " (O2/He %).",
                        null));
    }

    /**
     * DOMAIN §5.6: a stage draws more than its bank is estimated to hold before this fill (for an
     * update: the estimate plus what the replaced fill already took from that bank).
     */
    private static List<Warning> overdrawWarnings(
            final FillContent content, final TeamState state, final @Nullable StoredFill previous) {
        final var warnings = new ArrayList<Warning>();
        for (final var stage : content.stages()) {
            final var vesselId = stage.sourceVesselId();
            if (vesselId == null) {
                continue;
            }
            final double previousDrawLitres =
                    previous == null
                            ? 0
                            : previous.content().stages().stream()
                                    .filter(old -> Objects.equals(old.sourceVesselId(), vesselId))
                                    .mapToDouble(FillContent.Stage::drawLitres)
                                    .sum();
            final double availableLitres =
                    state.inventoryOf(vesselId).estimatedContentLitres() + previousDrawLitres;
            if (stage.drawLitres() > availableLitres) {
                warnings.add(
                        new Warning(
                                Warning.Code.SOURCE_OVERDRAWN,
                                "This takes more gas than "
                                        + bank(state, vesselId).label()
                                        + " is estimated to hold.",
                                stage.stageIndex()));
            }
        }
        return warnings;
    }

    // --- authorization ----------------------------------------------------------------------

    /** DOMAIN §10: its recorder, the member it is charged to, or an ADMIN. */
    private static void requireMayChange(final TeamMembership caller, final StoredFill fill) {
        final boolean charged =
                fill.content().target() instanceof FillTarget.Cylinder(_, _, final var memberId)
                        && memberId == caller.memberId();
        if (caller.role() != Role.ADMIN
                && fill.recordedByMemberId() != caller.memberId()
                && !charged) {
            throw new AccessDeniedException(
                    "Only a team admin, the member who recorded this fill or the member it is"
                            + " charged to can change it.");
        }
    }

    // --- responses --------------------------------------------------------------------------

    /** A stored fill priced from the snapshot, like any GET. */
    private Fill fillOf(final TeamMembership caller, final StoredFill stored) {
        final var state = snapshots.snapshot(caller);
        final var content = stored.content();
        final var prices = new Prices(state).of(content, stored.isVoided());
        return fillOf(stored, previewOf(content, prices, analysisWarnings(content), state), state);
    }

    private static Fill fillOf(
            final StoredFill stored, final FillPreview preview, final TeamState state) {
        final var content = stored.content();
        return new Fill(
                preview.endPressureBar(),
                preview.endMix(),
                preview.stages(),
                preview.totalCharge(),
                preview.warnings(),
                stored.fillId(),
                content.filledAt(),
                content.target() instanceof FillTarget.Vessel(final var vesselId, _)
                        ? new FillTarget.Vessel(vesselId, bank(state, vesselId).label())
                        : content.target(),
                chargedMember(content.target(), state),
                state.member(stored.recordedByMemberId()),
                content.startPressureBar(),
                content.startMix(),
                content.endAnalysis(),
                content.notes(),
                status(stored),
                stored.voidReason(),
                content.calcModelVersion(),
                stored.createdAt(),
                stored.updatedAt());
    }

    /** {@code prices} line up with the content's stages; {@code state} only supplies the names. */
    private static FillPreview previewOf(
            final FillContent content,
            final List<StagePrice> prices,
            final List<Warning> warnings,
            final TeamState state) {
        final var results = new ArrayList<StageResult>();
        for (int i = 0; i < content.stages().size(); i++) {
            final var stage = content.stages().get(i);
            final var price = prices.get(i);
            final var vesselId = stage.sourceVesselId();
            results.add(
                    new StageResult(
                            stage.stageIndex(),
                            vesselId == null
                                    ? new StageSource.Air()
                                    : new StageSource.Vessel(
                                            vesselId, bank(state, vesselId).label()),
                            stage.sourceMix(),
                            stage.pressureAfterBar(),
                            stage.addedLitres(),
                            stage.addedO2Litres(),
                            stage.addedHeLitres(),
                            stage.lineLossLitres(),
                            stage.drawLitres(),
                            Money.amount(price.charge()),
                            price.isEstimate(),
                            stage.sourcePressureAfterBar()));
        }
        return new FillPreview(
                content.endPressureBar(), content.endMix(), results, total(prices), warnings);
    }

    private static FillSummary summary(final StoredFill fill, final Prices prices) {
        final var content = fill.content();
        final var state = prices.state();
        return new FillSummary(
                fill.fillId(),
                content.filledAt(),
                chargedMember(content.target(), state),
                targetLabel(content.target(), state),
                content.endPressureBar(),
                content.endMix(),
                bankLitres(content, FillContent.Stage::addedHeLitres),
                bankLitres(content, FillContent.Stage::addedO2Litres),
                total(prices.of(content, fill.isVoided())),
                status(fill));
    }

    /** Σ of a gas added from banks: the O2 in compressor air is not bought gas. */
    private static double bankLitres(
            final FillContent content, final ToDoubleFunction<FillContent.Stage> litres) {
        return content.stages().stream()
                .filter(stage -> stage.sourceVesselId() != null)
                .mapToDouble(litres)
                .sum();
    }

    private static String targetLabel(final FillTarget target, final TeamState state) {
        return switch (target) {
            case FillTarget.Cylinder(final var volume, final var label, _) ->
                    label != null
                            ? label
                            : BigDecimal.valueOf(volume).stripTrailingZeros().toPlainString()
                                    + " L cylinder";
            case FillTarget.Vessel(final var vesselId, _) -> bank(state, vesselId).label();
        };
    }

    private static @Nullable MemberRef chargedMember(
            final FillTarget target, final TeamState state) {
        return target instanceof FillTarget.Cylinder(_, _, final var memberId)
                ? state.member(memberId)
                : null;
    }

    private static FillStatus status(final StoredFill fill) {
        return fill.isVoided() ? FillStatus.VOIDED : FillStatus.ACTIVE;
    }

    /** round(Σ unrounded stage charges), DOMAIN §3. */
    private static BigDecimal total(final List<StagePrice> prices) {
        return Money.amount(
                prices.stream().map(StagePrice::charge).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static String mixLabel(final GasMix mix) {
        return Math.round(mix.o2() * 100) + "/" + Math.round(mix.he() * 100);
    }

    /** A vessel of this team; ids in a request body that are not (or not any more) are a 400. */
    private static VesselRow bank(final TeamState state, final long vesselId) {
        return state.facts().vessels().stream()
                .filter(row -> row.vesselId() == vesselId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown bank."));
    }
}
