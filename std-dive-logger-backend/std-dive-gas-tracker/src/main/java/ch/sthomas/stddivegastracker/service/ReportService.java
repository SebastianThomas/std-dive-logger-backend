package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationEngine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput;
import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator;
import ch.sthomas.stddivegastracker.data.service.FillDataService;
import ch.sthomas.stddivegastracker.data.service.PaymentDataService;
import ch.sthomas.stddivegastracker.model.Money;
import ch.sthomas.stddivegastracker.model.report.ConsumptionReport;
import ch.sthomas.stddivegastracker.model.report.LossesReport;
import ch.sthomas.stddivegastracker.model.report.PolicyComparison;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.TeamFacts.VesselRow;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.TeamSnapshotService.TeamState;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** API.md §6 reports, all read-only views of the {@link TeamSnapshotService} state. */
@Service
public final class ReportService {

    // Open-ended periods: no fill predates the epoch, none is recorded in year 9999.
    private static final Instant NO_START = Instant.EPOCH;
    private static final Instant NO_END = Instant.parse("9999-01-01T00:00:00Z");

    private final TeamAccessService teamAccess;
    private final TeamSnapshotService snapshots;
    private final FillDataService fillDataService;
    private final PaymentDataService paymentDataService;

    public ReportService(
            final TeamAccessService teamAccess,
            final TeamSnapshotService snapshots,
            final FillDataService fillDataService,
            final PaymentDataService paymentDataService) {
        this.teamAccess = teamAccess;
        this.snapshots = snapshots;
        this.fillDataService = fillDataService;
        this.paymentDataService = paymentDataService;
    }

    /**
     * Members with at least one cylinder fill in {@code [from, to)}, most expensive first. Charges
     * are at today's prices (DOMAIN §7.2: one price per vessel at any moment).
     */
    public ConsumptionReport consumption(
            final long teamId,
            final GasUser user,
            final @Nullable Instant from,
            final @Nullable Instant to) {
        final var start = from == null ? NO_START : from;
        final var end = to == null ? NO_END : to;
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("'from' must be before 'to'.");
        }
        final var caller = teamAccess.requireMember(teamId, user);
        final var fills = fillDataService.cylinderFillCounts(teamId, start, end);
        final var state = snapshots.snapshot(caller);

        final var o2Litres = new HashMap<Long, Double>();
        final var heLitres = new HashMap<Long, Double>();
        final var charges = new HashMap<Long, BigDecimal>();
        for (final var stage : state.facts().stages()) {
            final var memberId = stage.chargedMemberId();
            if (memberId == null
                    || stage.sourceVesselId() == null
                    || stage.filledAt().isBefore(start)
                    || !stage.filledAt().isBefore(end)) {
                continue;
            }
            o2Litres.merge(memberId, stage.addedO2Litres(), Double::sum);
            heLitres.merge(memberId, stage.addedHeLitres(), Double::sum);
            charges.merge(
                    memberId,
                    state.allocation().drawCharge(stage.stageId()).charge(),
                    BigDecimal::add);
        }
        final var rows =
                fills.entrySet().stream()
                        .map(
                                entry ->
                                        new ConsumptionReport.Row(
                                                state.member(entry.getKey()),
                                                o2Litres.getOrDefault(entry.getKey(), 0.0),
                                                heLitres.getOrDefault(entry.getKey(), 0.0),
                                                entry.getValue(),
                                                Money.amount(
                                                        charges.getOrDefault(
                                                                entry.getKey(), BigDecimal.ZERO))))
                        .sorted(
                                Comparator.comparing(ConsumptionReport.Row::charge)
                                        .reversed()
                                        .thenComparing(
                                                row -> row.member().displayName(),
                                                String.CASE_INSENSITIVE_ORDER))
                        .toList();
        return new ConsumptionReport(rows);
    }

    /** Every closed bank with its loss report (DOMAIN §6), latest closing first. */
    public LossesReport losses(final long teamId, final GasUser user) {
        final var state = snapshots.snapshot(teamAccess.requireMember(teamId, user));
        final var closed =
                state.facts().vessels().stream()
                        .filter(row -> row.closedAt() != null)
                        .sorted(
                                Comparator.comparing(
                                                (VesselRow row) ->
                                                        Objects.requireNonNull(row.closedAt()))
                                        .reversed()
                                        .thenComparing(
                                                VesselRow::vesselId, Comparator.reverseOrder()))
                        .map(
                                row ->
                                        new LossesReport.Entry(
                                                VesselService.summary(row, state),
                                                Objects.requireNonNull(
                                                        VesselService.lossReport(
                                                                row,
                                                                state.inventoryOf(row.vesselId()),
                                                                state.allocation()
                                                                        .vessel(row.vesselId())))))
                        .toList();
        return new LossesReport(closed);
    }

    /**
     * DOMAIN §7.6: balances with every bank TRUE_UP and with every bank POOLED (payments included).
     * Members as on the balances list: everyone but LEFT members at 0.00 under both policies.
     */
    public PolicyComparison policyComparison(final long teamId, final GasUser user) {
        final var caller = teamAccess.requireMember(teamId, user);
        final var payments = BalanceService.calcPayments(paymentDataService.list(teamId));
        final var state = snapshots.snapshot(caller);
        final var trueUp = balancesUnder(LossPolicy.TRUE_UP, state, payments);
        final var pooled = balancesUnder(LossPolicy.POOLED, state, payments);

        final var rows =
                state.facts().members().stream()
                        .map(
                                member -> {
                                    // delta of the shown (rounded) values, so the row adds up
                                    final var withTrueUp =
                                            Money.amount(
                                                    trueUp.getOrDefault(
                                                            member.memberId(), BigDecimal.ZERO));
                                    final var withPool =
                                            Money.amount(
                                                    pooled.getOrDefault(
                                                            member.memberId(), BigDecimal.ZERO));
                                    return new Shown(
                                            member.status(),
                                            new PolicyComparison.Row(
                                                    state.member(member.memberId()),
                                                    withTrueUp,
                                                    withPool,
                                                    withPool.subtract(withTrueUp)));
                                })
                        .filter(
                                shown ->
                                        shown.status() != MemberStatus.LEFT
                                                || shown.row().trueUp().signum() != 0
                                                || shown.row().pooled().signum() != 0)
                        .map(Shown::row)
                        .sorted(
                                Comparator.comparing(
                                                (PolicyComparison.Row row) ->
                                                        row.member().displayName(),
                                                String.CASE_INSENSITIVE_ORDER)
                                        .thenComparingLong(row -> row.member().memberId()))
                        .toList();
        return new PolicyComparison(rows);
    }

    private record Shown(MemberStatus status, PolicyComparison.Row row) {}

    /**
     * Unrounded balance per member with every vessel's policy overridden (V_in/V_res unchanged).
     */
    private static Map<Long, BigDecimal> balancesUnder(
            final LossPolicy policy,
            final TeamState state,
            final List<BalanceCalculator.Payment> payments) {
        final var input = state.input();
        final var overridden =
                new AllocationInput(
                        input.vessels().stream()
                                .map(
                                        vessel ->
                                                new AllocationInput.Vessel(
                                                        vessel.vesselId(),
                                                        policy,
                                                        vessel.status(),
                                                        vessel.costLines(),
                                                        vessel.inflowLitres(),
                                                        vessel.residualLitres(),
                                                        vessel.closingParticipantIds()))
                                .toList(),
                        input.draws(),
                        input.activeMemberIds());
        return BalanceCalculator.calculate(
                        overridden, AllocationEngine.allocate(overridden), payments)
                .stream()
                .collect(
                        Collectors.toMap(
                                BalanceCalculator.MemberBalance::memberId,
                                BalanceCalculator.MemberBalance::balance));
    }
}
