package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.VesselAllocation;
import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay;
import ch.sthomas.stddivegastracker.calc.inventory.InventoryReplay.ReadingResult;
import ch.sthomas.stddivegastracker.data.service.VesselDataService;
import ch.sthomas.stddivegastracker.model.Money;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.VesselRow;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.vessel.CloseVesselRequest;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.model.vessel.ReadingInput;
import ch.sthomas.stddivegastracker.model.vessel.VesselCreate;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail.CostLine;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail.LossReport;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail.Reading;
import ch.sthomas.stddivegastracker.model.vessel.VesselStatusFilter;
import ch.sthomas.stddivegastracker.model.vessel.VesselSummary;
import ch.sthomas.stddivegastracker.model.vessel.VesselUpdate;
import ch.sthomas.stddivegastracker.service.TeamSnapshotService.TeamState;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * API.md §3; authorization per DOMAIN §10. Responses come from a fresh {@link TeamSnapshotService}
 * state (a price depends on the whole team, §7.4); money is rounded here, at the boundary (§3).
 */
@Service
public final class VesselService {

    private static final Comparator<VesselRow> IN_SERVICE_ORDER =
            Comparator.comparing(VesselRow::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(VesselRow::vesselId);
    private static final Comparator<VesselRow> CLOSED_ORDER =
            Comparator.comparing((VesselRow row) -> Objects.requireNonNull(row.closedAt()))
                    .reversed()
                    .thenComparingLong(VesselRow::vesselId);

    private final TeamAccessService teamAccess;
    private final VesselDataService vesselDataService;
    private final TeamSnapshotService snapshots;

    public VesselService(
            final TeamAccessService teamAccess,
            final VesselDataService vesselDataService,
            final TeamSnapshotService snapshots) {
        this.teamAccess = teamAccess;
        this.vesselDataService = vesselDataService;
        this.snapshots = snapshots;
    }

    /**
     * IN_SERVICE by label, CLOSED by closing time (newest first); ALL = the former, then the
     * latter.
     */
    public List<VesselSummary> list(
            final long teamId, final GasUser user, final VesselStatusFilter filter) {
        final var state = snapshots.snapshot(teamAccess.requireMember(teamId, user));
        final var inService =
                state.facts().vessels().stream()
                        .filter(row -> row.closedAt() == null)
                        .sorted(IN_SERVICE_ORDER)
                        .toList();
        final var closed =
                state.facts().vessels().stream()
                        .filter(row -> row.closedAt() != null)
                        .sorted(CLOSED_ORDER)
                        .toList();
        final var shown =
                switch (filter) {
                    case IN_SERVICE -> inService;
                    case CLOSED -> closed;
                    case ALL -> Stream.concat(inService.stream(), closed.stream()).toList();
                };
        return shown.stream().map(row -> summary(row, state)).toList();
    }

    public VesselDetail get(final long teamId, final long vesselId, final GasUser user) {
        return detail(teamAccess.requireMember(teamId, user), vesselId);
    }

    /** An initial cost line follows the same payer rule as any other (DOMAIN §10). */
    public VesselDetail create(final long teamId, final GasUser user, final VesselCreate request) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var costLine = request.initialCostLine();
        if (costLine != null) {
            requirePayerAllowed(caller, user, costLine);
        }
        return detail(caller, vesselDataService.create(caller, request));
    }

    /**
     * Label, notes and residual pressure: the creator or an ADMIN; mix and loss policy: ADMIN (the
     * data service refuses the policy once CLOSED).
     */
    public VesselDetail update(
            final long teamId,
            final long vesselId,
            final GasUser user,
            final VesselUpdate request) {
        final var caller = teamAccess.requireMember(teamId, user);
        if (request.mix() != null || request.lossPolicy() != null) {
            teamAccess.requireAdmin(teamId, user);
        } else {
            requireAdminOrCreator(caller, vesselId, "edit");
        }
        vesselDataService.update(teamId, vesselId, request);
        return detail(caller, vesselId);
    }

    /** ADMIN, or the member who added the vessel (DOMAIN §10). */
    public VesselDetail close(
            final long teamId,
            final long vesselId,
            final GasUser user,
            final CloseVesselRequest request) {
        final var caller = teamAccess.requireMember(teamId, user);
        requireAdminOrCreator(caller, vesselId, "close");
        vesselDataService.close(teamId, vesselId, request);
        return detail(caller, vesselId);
    }

    public VesselDetail reopen(final long teamId, final long vesselId, final GasUser user) {
        final var caller = teamAccess.requireAdmin(teamId, user);
        vesselDataService.reopen(teamId, vesselId);
        return detail(caller, vesselId);
    }

    public VesselDetail addCostLine(
            final long teamId,
            final long vesselId,
            final GasUser user,
            final CostLineInput request) {
        final var caller = teamAccess.requireMember(teamId, user);
        requirePayerAllowed(caller, user, request);
        vesselDataService.addCostLine(teamId, vesselId, request);
        return detail(caller, vesselId);
    }

    public VesselDetail deleteCostLine(
            final long teamId, final long vesselId, final long costLineId, final GasUser user) {
        final var caller = teamAccess.requireAdmin(teamId, user);
        vesselDataService.deleteCostLine(teamId, vesselId, costLineId);
        return detail(caller, vesselId);
    }

    public VesselDetail addReading(
            final long teamId,
            final long vesselId,
            final GasUser user,
            final ReadingInput request) {
        final var caller = teamAccess.requireMember(teamId, user);
        vesselDataService.addReading(teamId, vesselId, caller.memberId(), request);
        return detail(caller, vesselId);
    }

    /** A cost line for another payer needs an ADMIN; the payer's validity is checked on write. */
    private void requirePayerAllowed(
            final TeamMembership caller, final GasUser user, final CostLineInput costLine) {
        if (costLine.payerMemberId() != caller.memberId()) {
            teamAccess.requireAdmin(caller.teamId(), user);
        }
    }

    private void requireAdminOrCreator(
            final TeamMembership caller, final long vesselId, final String action) {
        if (caller.role() != Role.ADMIN
                && caller.memberId() != vesselDataService.creatorOf(caller.teamId(), vesselId)) {
            throw new AccessDeniedException(
                    "Only a team admin or the member who added this bank can " + action + " it.");
        }
    }

    private VesselDetail detail(final TeamMembership caller, final long vesselId) {
        final var state = snapshots.snapshot(caller);
        final var row =
                state.facts().vessels().stream()
                        .filter(vessel -> vessel.vesselId() == vesselId)
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("Bank not found."));
        return detail(row, state);
    }

    static VesselSummary summary(final VesselRow row, final TeamState state) {
        final var inventory = state.inventoryOf(row.vesselId());
        final var allocation = state.allocation().vessel(row.vesselId());
        return new VesselSummary(
                row.vesselId(),
                row.label(),
                row.mix().gasClass(),
                row.mix(),
                row.waterVolumeLitres(),
                status(row),
                row.lossPolicy(),
                inventory.estimatedPressureBar(),
                inventory.estimatedContentLitres(),
                inventory.usableContentLitres(),
                Money.unitPrice(allocation.rate()),
                allocation.provisional(),
                row.openedAt(),
                row.closedAt());
    }

    private static VesselDetail detail(final VesselRow row, final TeamState state) {
        final var summary = summary(row, state);
        final var inventory = state.inventoryOf(row.vesselId());
        final var allocation = state.allocation().vessel(row.vesselId());
        final var costLines =
                state.facts().costLines().stream()
                        .filter(line -> line.vesselId() == row.vesselId())
                        .map(
                                line ->
                                        new CostLine(
                                                line.costLineId(),
                                                line.payerMemberId(),
                                                line.amount(),
                                                line.paidAt(),
                                                line.note(),
                                                state.member(line.payerMemberId())))
                        .toList();
        final var bookByReading =
                inventory.readings().stream()
                        .collect(Collectors.toMap(ReadingResult::readingId, Function.identity()));
        final var readings =
                state.facts().readings().stream()
                        .filter(reading -> reading.vesselId() == row.vesselId())
                        .map(
                                reading -> {
                                    final var book =
                                            Objects.requireNonNull(
                                                    bookByReading.get(reading.readingId()));
                                    return new Reading(
                                            reading.readingId(),
                                            reading.pressureBar(),
                                            reading.measuredAt(),
                                            state.member(reading.createdByMemberId()),
                                            book.bookPressureBar(),
                                            book.discrepancyLitres());
                                })
                        .toList();
        return new VesselDetail(
                summary.vesselId(),
                summary.label(),
                summary.gasClass(),
                summary.mix(),
                summary.waterVolumeLitres(),
                summary.status(),
                summary.lossPolicy(),
                summary.estimatedPressureBar(),
                summary.estimatedContentLitres(),
                summary.usableContentLitres(),
                summary.rate(),
                summary.rateIsEstimate(),
                summary.openedAt(),
                summary.closedAt(),
                row.initialPressureBar(),
                row.expectedResidualPressureBar(),
                row.closingPressureBar(),
                row.notes(),
                state.member(row.createdByMemberId()),
                Money.amount(allocation.totalCost()),
                Money.amount(allocation.allocated()),
                Money.amount(allocation.unallocated()),
                inventory.inflowLitres(),
                inventory.drawnLitres(),
                inventory.bookContentLitres(),
                costLines,
                readings,
                lossReport(row, inventory, allocation));
    }

    /**
     * Closed vessels only. POOLED shows the loss, TRUE_UP with no draws the write-off: both are the
     * sum of the vessel's loss shares (DOMAIN §7.3).
     */
    static @Nullable LossReport lossReport(
            final VesselRow row,
            final InventoryReplay.Result inventory,
            final VesselAllocation allocation) {
        final var report = inventory.lossReport();
        if (report == null) {
            return null;
        }
        final var shares =
                Money.amount(
                        allocation.lossShares().stream()
                                .map(LossShare::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        final var writeOff =
                row.lossPolicy() == LossPolicy.TRUE_UP && allocation.drawnLitres().signum() == 0;
        return new LossReport(
                report.inflowLitres(),
                report.drawnLitres(),
                report.lineLossLitres(),
                report.residualLitres(),
                report.unaccountedLitres(),
                report.lossFraction(),
                row.lossPolicy() == LossPolicy.POOLED ? shares : null,
                writeOff ? shares : null);
    }

    private static VesselStatus status(final VesselRow row) {
        return row.closedAt() == null ? VesselStatus.IN_SERVICE : VesselStatus.CLOSED;
    }
}
