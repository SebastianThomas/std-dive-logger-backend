package ch.sthomas.stddivegastracker.calc.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.DrawCharge;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.LossShare;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.MemberAllocation;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationResult.VesselAllocation;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationScenarios.Mode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Contract of the {@link LossPolicy} family (DOMAIN §7.5): the same invariant suite runs on seeded
 * random DAGs whose vessels are all TRUE_UP, all POOLED, or mixed. Covers #1, #2, #4, #5,
 * "unallocated = 0 when closed", the §7.3 participant fallbacks and rate formulas. A failure
 * reports mode, seed and scenario index; scenario {@code i} is regenerated from {@code baseSeed +
 * i} alone via {@link AllocationScenarios}. (#3 is a balance identity, see {@code
 * calc.settlement.BalanceIdentityContractTest}; tenant isolation is a service concern.)
 */
final class AllocationPolicyContractTest {

    private static final int SCENARIOS = 1500;
    private static final int MIN_SPLITS = 50;
    private static final BigDecimal TOLERANCE = new BigDecimal("1e-9");

    /**
     * How often the generator reached the interesting corners (guards against a dull generator).
     */
    private static final class Coverage {
        int transfers;
        int chains;
        int writeOffs;
        int negativeLosses;
        int snapshotSplits;
        int activeMemberSplits;
        int payerSplits;
        int expectedFailures;
        int residualAboveInflow;
        int openTrueUp;
        int closedTrueUpWithDraws;
        int closedPooled;
        int provisionalVessels;
        int inheritedProvisional;
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void invariantsHoldForRandomDags(final Mode mode) {
        final var coverage = new Coverage();
        for (int i = 0; i < SCENARIOS; i++) {
            final long scenarioSeed = mode.baseSeed + i;
            final var input = AllocationScenarios.randomInput(new Random(scenarioSeed), mode);
            try {
                checkScenario(input, new Random(~scenarioSeed), coverage);
            } catch (final AssertionError | RuntimeException e) {
                throw new AssertionError(
                        "Contract violated: mode="
                                + mode
                                + ", baseSeed="
                                + mode.baseSeed
                                + ", scenario="
                                + i
                                + " (scenarioSeed="
                                + scenarioSeed
                                + ")\n"
                                + e.getMessage()
                                + "\ninput: "
                                + input,
                        e);
            }
        }

        assertThat(coverage.transfers).as("scenarios with vessel transfers").isGreaterThan(400);
        assertThat(coverage.chains).as("scenarios with a transfer chain").isGreaterThan(100);
        assertThat(coverage.residualAboveInflow).as("V_res > V_in").isGreaterThan(100);
        assertThat(coverage.snapshotSplits).as("splits by snapshot").isGreaterThan(MIN_SPLITS);
        assertThat(coverage.activeMemberSplits)
                .as("splits by active members")
                .isGreaterThan(MIN_SPLITS);
        assertThat(coverage.payerSplits).as("splits by payers").isGreaterThan(10);
        assertThat(coverage.expectedFailures).as("unresolvable participants").isGreaterThan(5);
        // Policy-specific corners only exist where that policy does; provisional needs TRUE_UP.
        assertThat(coverage.provisionalVessels > 0).isEqualTo(mode.hasTrueUp());
        assertThat(coverage.inheritedProvisional > 0).isEqualTo(mode.hasTrueUp());
        assertThat(coverage.openTrueUp > 0).isEqualTo(mode.hasTrueUp());
        assertThat(coverage.closedTrueUpWithDraws > 0).isEqualTo(mode.hasTrueUp());
        assertThat(coverage.writeOffs > 0).isEqualTo(mode.hasTrueUp());
        assertThat(coverage.closedPooled > 0).isEqualTo(mode.hasPooled());
        assertThat(coverage.negativeLosses > 0).isEqualTo(mode.hasPooled());
        if (mode.hasTrueUp()) {
            assertThat(coverage.openTrueUp).as("open TRUE_UP vessels").isGreaterThan(200);
            assertThat(coverage.inheritedProvisional)
                    .as("inherited provisional")
                    .isGreaterThan(200);
            assertThat(coverage.closedTrueUpWithDraws)
                    .as("closed TRUE_UP with draws")
                    .isGreaterThan(200);
            assertThat(coverage.writeOffs).as("TRUE_UP write-offs").isGreaterThan(MIN_SPLITS);
        }
        if (mode.hasPooled()) {
            assertThat(coverage.closedPooled).as("closed POOLED vessels").isGreaterThan(200);
            assertThat(coverage.negativeLosses).as("negative POOLED losses").isGreaterThan(50);
        }
    }

    // --- invariants ---

    private static void checkScenario(
            final AllocationInput input, final Random shuffleRnd, final Coverage coverage) {
        final var unresolvable = classifyClosings(input, coverage);
        if (!unresolvable.isEmpty()) {
            // Closed vessel with a loss to split but nobody to split it among (DOMAIN §7.3).
            coverage.expectedFailures++;
            assertThatThrownBy(() -> AllocationEngine.allocate(input))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(
                            e -> {
                                final var message = String.valueOf(e.getMessage());
                                assertThat(unresolvable)
                                        .anyMatch(
                                                id ->
                                                        message.contains(
                                                                "Vessel " + id + " is closed"));
                            });
            return;
        }

        final var result = AllocationEngine.allocate(input);
        checkStructure(input, result);
        checkVessels(input, result, coverage);
        checkConservation(input, result);
        checkMemberTotals(input, result);

        // Deterministic, and independent of input order (§7.5 #5).
        assertThat(AllocationEngine.allocate(input)).isEqualTo(result);
        assertSameResult(result, AllocationEngine.allocate(shuffled(input, shuffleRnd)));
    }

    /**
     * Independent oracle: which closed vessels must split a loss but have nobody to split among.
     */
    private static Set<Long> classifyClosings(
            final AllocationInput input, final Coverage coverage) {
        final var drawnByVessel = new HashMap<Long, Double>();
        final var sources = new HashSet<Long>();
        final var targets = new HashSet<Long>();
        for (final var draw : input.draws()) {
            drawnByVessel.merge(draw.sourceVesselId(), draw.litres(), Double::sum);
            if (draw.beneficiary() instanceof ToVessel(final var targetId)) {
                sources.add(draw.sourceVesselId());
                targets.add(targetId);
            }
        }
        if (!sources.isEmpty()) {
            coverage.transfers++;
        }
        if (sources.stream().anyMatch(targets::contains)) {
            coverage.chains++;
        }

        final var unresolvable = new TreeSet<Long>();
        for (final var v : input.vessels()) {
            if (v.residualLitres() > v.inflowLitres()) {
                coverage.residualAboveInflow++;
            }
            final boolean splits =
                    v.status() == VesselStatus.CLOSED
                            && (v.policy() == LossPolicy.POOLED
                                    || drawnByVessel.getOrDefault(v.vesselId(), 0.0) == 0);
            if (!splits) {
                continue;
            }
            if (!v.closingParticipantIds().isEmpty()) {
                coverage.snapshotSplits++;
            } else if (!input.activeMemberIds().isEmpty()) {
                coverage.activeMemberSplits++;
            } else if (directCost(v).signum() > 0) {
                coverage.payerSplits++;
            } else {
                unresolvable.add(v.vesselId());
            }
        }
        return unresolvable;
    }

    private static void checkStructure(final AllocationInput input, final AllocationResult result) {
        assertThat(result.vessels())
                .extracting(VesselAllocation::vesselId)
                .isSorted()
                .containsExactlyInAnyOrderElementsOf(
                        input.vessels().stream().map(Vessel::vesselId).toList());
        assertThat(result.drawCharges())
                .extracting(DrawCharge::drawId)
                .isSorted()
                .containsExactlyInAnyOrderElementsOf(
                        input.draws().stream().map(Draw::drawId).toList());
        assertThat(result.members())
                .extracting(MemberAllocation::memberId)
                .isSorted()
                .doesNotHaveDuplicates();
    }

    private static void checkVessels(
            final AllocationInput input, final AllocationResult result, final Coverage coverage) {
        final var chargeByDraw = new HashMap<Long, BigDecimal>();
        result.drawCharges().forEach(c -> chargeByDraw.put(c.drawId(), c.charge()));
        final var provisional = AllocationScenarios.provisionalVessels(input);

        for (final var v : input.vessels()) {
            final var va = result.vessel(v.vesselId());
            final boolean closed = v.status() == VesselStatus.CLOSED;
            final var what = "vessel " + v.vesselId();

            // DOMAIN §7.1: open TRUE_UP or (transitively) fed by one - an independent oracle.
            assertThat(va.provisional())
                    .as("provisional of " + what)
                    .isEqualTo(provisional.contains(v.vesselId()));
            if (va.provisional()) {
                coverage.provisionalVessels++;
                if (closed || v.policy() == LossPolicy.POOLED) {
                    coverage.inheritedProvisional++;
                }
            }

            var upstream = BigDecimal.ZERO;
            var drawn = BigDecimal.ZERO;
            var charged = BigDecimal.ZERO;
            for (final var d : input.draws()) {
                final var charge = Objects.requireNonNull(chargeByDraw.get(d.drawId()));
                assertThat(charge).as("charge of draw " + d.drawId()).isNotNegative();
                if (d.beneficiary() instanceof ToVessel(final var targetId)
                        && targetId == v.vesselId()) {
                    upstream = upstream.add(charge);
                }
                if (d.sourceVesselId() == v.vesselId()) {
                    drawn = drawn.add(BigDecimal.valueOf(d.litres()));
                    charged = charged.add(charge);
                    // charge(d) = rate(source) * litres(d)
                    assertClose(
                            charge,
                            va.rate().multiply(BigDecimal.valueOf(d.litres())),
                            "charge of draw " + d.drawId());
                }
            }
            final var inflow = BigDecimal.valueOf(v.inflowLitres());
            final var usable = inflow.subtract(BigDecimal.valueOf(v.residualLitres()));
            var shares = BigDecimal.ZERO;
            for (final var share : va.lossShares()) {
                shares = shares.add(share.amount());
            }

            // §7.2 definitions.
            assertClose(va.totalCost(), directCost(v).add(upstream), "C of " + what);
            assertClose(va.drawnLitres(), drawn, "D of " + what);
            assertClose(va.allocated(), charged.add(shares), "allocated of " + what);
            assertClose(va.unallocated(), va.totalCost().subtract(va.allocated()), "unallocated");
            assertThat(va.rate()).as("rate of " + what).isNotNegative(); // §7.5 #4
            assertThat(va.lossShares())
                    .extracting(LossShare::memberId)
                    .isSorted()
                    .doesNotHaveDuplicates();

            if (!closed) {
                assertThat(va.lossShares()).as("open " + what).isEmpty();
            } else {
                assertClose(va.unallocated(), BigDecimal.ZERO, "closed " + what + " unallocated");
            }

            // §7.3 rates.
            if (v.policy() == LossPolicy.POOLED) {
                if (inflow.signum() == 0) {
                    assertThat(va.rate()).isEqualByComparingTo(BigDecimal.ZERO);
                } else {
                    assertClose(va.rate().multiply(inflow), va.totalCost(), "rate * V_in");
                }
                if (closed) {
                    coverage.closedPooled++;
                    final var loss = va.totalCost().subtract(va.rate().multiply(drawn));
                    assertClose(shares, loss, "POOLED loss of " + what);
                    assertSplit(v, va, input.activeMemberIds(), loss);
                    if (shares.signum() < 0) {
                        coverage.negativeLosses++;
                    }
                }
            } else if (!closed) {
                coverage.openTrueUp++;
                assertThat(va.provisional()).isTrue();
                final var denominator = usable.max(drawn);
                if (denominator.signum() <= 0) {
                    assertThat(va.rate()).isEqualByComparingTo(BigDecimal.ZERO);
                } else {
                    assertClose(va.rate().multiply(denominator), va.totalCost(), "rate * max(..)");
                }
                // An open TRUE_UP vessel never hands out more than it cost.
                assertThat(va.unallocated())
                        .as("open TRUE_UP unallocated of " + what)
                        .isGreaterThan(TOLERANCE.negate());
            } else {
                if (drawn.signum() > 0) {
                    // §7.5 #2
                    coverage.closedTrueUpWithDraws++;
                    assertClose(charged, va.totalCost(), "Σ charges = C of " + what);
                    assertThat(va.lossShares()).isEmpty();
                } else {
                    coverage.writeOffs++;
                    assertThat(va.rate()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertClose(shares, va.totalCost(), "write-off of " + what);
                    assertSplit(v, va, input.activeMemberIds(), va.totalCost());
                }
            }
        }
    }

    /** §7.3 participants: snapshot, else active members (equal), else payers pro-rata. */
    private static void assertSplit(
            final Vessel v,
            final VesselAllocation va,
            final Set<Long> active,
            final BigDecimal amount) {
        final var equal =
                new TreeSet<Long>(
                        v.closingParticipantIds().isEmpty() ? active : v.closingParticipantIds());
        if (!equal.isEmpty()) {
            assertThat(va.lossShares())
                    .extracting(LossShare::memberId)
                    .containsExactlyElementsOf(equal);
            final var each =
                    amount.divide(BigDecimal.valueOf(equal.size()), MathContext.DECIMAL128);
            va.lossShares().forEach(share -> assertClose(share.amount(), each, "equal share"));
            return;
        }
        final var paid = new TreeMap<Long, BigDecimal>();
        v.costLines().forEach(l -> paid.merge(l.payerMemberId(), l.amount(), BigDecimal::add));
        paid.values().removeIf(amountPaid -> amountPaid.signum() == 0);
        assertThat(va.lossShares())
                .extracting(LossShare::memberId)
                .containsExactlyElementsOf(paid.keySet());
        final var totalPaid = paid.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (final var share : va.lossShares()) {
            final var expected =
                    amount.multiply(Objects.requireNonNull(paid.get(share.memberId())))
                            .divide(totalPaid, MathContext.DECIMAL128);
            assertClose(share.amount(), expected, "pro-rata share");
        }
    }

    /** §7.5 #1: Σ C_direct = Σ member fill charges + Σ loss shares + Σ open unallocated. */
    private static void checkConservation(
            final AllocationInput input, final AllocationResult result) {
        var direct = BigDecimal.ZERO;
        var openUnallocated = BigDecimal.ZERO;
        for (final var v : input.vessels()) {
            direct = direct.add(directCost(v));
            if (v.status() == VesselStatus.IN_SERVICE) {
                openUnallocated = openUnallocated.add(result.vessel(v.vesselId()).unallocated());
            }
        }
        var memberTotals = BigDecimal.ZERO;
        for (final var m : result.members()) {
            memberTotals = memberTotals.add(m.fillCharges()).add(m.lossShares());
        }
        assertClose(direct, memberTotals.add(openUnallocated), "conservation");
    }

    private static void checkMemberTotals(
            final AllocationInput input, final AllocationResult result) {
        final var fill = new HashMap<Long, BigDecimal>();
        for (final var d : input.draws()) {
            if (d.beneficiary() instanceof ToMember(final var memberId)) {
                fill.merge(memberId, result.drawCharge(d.drawId()).charge(), BigDecimal::add);
            }
        }
        final var loss = new HashMap<Long, BigDecimal>();
        for (final var va : result.vessels()) {
            va.lossShares().forEach(s -> loss.merge(s.memberId(), s.amount(), BigDecimal::add));
        }
        for (final var m : result.members()) {
            assertClose(m.fillCharges(), fill.getOrDefault(m.memberId(), BigDecimal.ZERO), "fill");
            assertClose(m.lossShares(), loss.getOrDefault(m.memberId(), BigDecimal.ZERO), "loss");
        }
        for (final long memberId : input.activeMemberIds()) {
            assertThat(result.member(memberId)).isNotNull();
        }
        // Every member that was charged or shared a loss is listed.
        final var listed = new HashSet<Long>();
        result.members().forEach(m -> listed.add(m.memberId()));
        assertThat(listed).containsAll(fill.keySet()).containsAll(loss.keySet());
    }

    private static BigDecimal directCost(final Vessel vessel) {
        var total = BigDecimal.ZERO;
        for (final var line : vessel.costLines()) {
            total = total.add(line.amount());
        }
        return total;
    }

    private static void assertClose(
            final BigDecimal actual, final BigDecimal expected, final String what) {
        assertThat(actual.subtract(expected).abs())
                .as("%s: actual=%s expected=%s", what, actual, expected)
                .isLessThan(TOLERANCE);
    }

    // --- order independence ---

    private static AllocationInput shuffled(final AllocationInput input, final Random rnd) {
        final var vessels = new ArrayList<Vessel>();
        for (final var v : input.vessels()) {
            final var costLines = new ArrayList<CostLine>(v.costLines());
            final var participants = new ArrayList<Long>(v.closingParticipantIds());
            Collections.shuffle(costLines, rnd);
            Collections.shuffle(participants, rnd);
            vessels.add(
                    new Vessel(
                            v.vesselId(),
                            v.policy(),
                            v.status(),
                            costLines,
                            v.inflowLitres(),
                            v.residualLitres(),
                            participants));
        }
        final var draws = new ArrayList<Draw>(input.draws());
        Collections.shuffle(vessels, rnd);
        Collections.shuffle(draws, rnd);
        return new AllocationInput(vessels, draws, new HashSet<>(input.activeMemberIds()));
    }

    /** Same ids in the same order and numerically identical values (scale-insensitive). */
    private static void assertSameResult(
            final AllocationResult expected, final AllocationResult actual) {
        assertThat(actual.vessels()).hasSameSizeAs(expected.vessels());
        for (int i = 0; i < expected.vessels().size(); i++) {
            final var e = expected.vessels().get(i);
            final var a = actual.vessels().get(i);
            assertThat(a.vesselId()).isEqualTo(e.vesselId());
            assertThat(a.provisional()).isEqualTo(e.provisional());
            assertThat(a.rate()).as("rate of %d", e.vesselId()).isEqualByComparingTo(e.rate());
            assertThat(a.totalCost())
                    .as("C of %d", e.vesselId())
                    .isEqualByComparingTo(e.totalCost());
            assertThat(a.drawnLitres()).isEqualByComparingTo(e.drawnLitres());
            assertThat(a.allocated())
                    .as("allocated of %d", e.vesselId())
                    .isEqualByComparingTo(e.allocated());
            assertThat(a.unallocated())
                    .as("unallocated of %d", e.vesselId())
                    .isEqualByComparingTo(e.unallocated());
            assertThat(a.lossShares()).hasSameSizeAs(e.lossShares());
            for (int s = 0; s < e.lossShares().size(); s++) {
                assertThat(a.lossShares().get(s).memberId())
                        .isEqualTo(e.lossShares().get(s).memberId());
                assertThat(a.lossShares().get(s).amount())
                        .isEqualByComparingTo(e.lossShares().get(s).amount());
            }
        }
        assertThat(actual.drawCharges()).hasSameSizeAs(expected.drawCharges());
        for (int i = 0; i < expected.drawCharges().size(); i++) {
            assertThat(actual.drawCharges().get(i).drawId())
                    .isEqualTo(expected.drawCharges().get(i).drawId());
            assertThat(actual.drawCharges().get(i).charge())
                    .isEqualByComparingTo(expected.drawCharges().get(i).charge());
        }
        assertThat(actual.members()).hasSameSizeAs(expected.members());
        for (int i = 0; i < expected.members().size(); i++) {
            final var e = expected.members().get(i);
            final var a = actual.members().get(i);
            assertThat(a.memberId()).isEqualTo(e.memberId());
            assertThat(a.fillCharges()).isEqualByComparingTo(e.fillCharges());
            assertThat(a.lossShares()).isEqualByComparingTo(e.lossShares());
        }
    }
}
