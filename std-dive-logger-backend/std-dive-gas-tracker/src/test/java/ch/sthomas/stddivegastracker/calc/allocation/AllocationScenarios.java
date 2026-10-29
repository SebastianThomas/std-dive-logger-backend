package ch.sthomas.stddivegastracker.calc.allocation;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToMember;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Beneficiary.ToVessel;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.CostLine;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Draw;
import ch.sthomas.stddivegastracker.calc.allocation.AllocationInput.Vessel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Seeded random allocation scenarios (random DAGs of vessels) shared by the contract tests of the
 * {@link LossPolicy} family and of the balance identity (DOMAIN §7.5). Scenario {@code i} of a mode
 * is {@code randomInput(new Random(mode.baseSeed + i), mode)}.
 */
public final class AllocationScenarios {

    /** Members 1..MEMBERS draw; payers also include member MEMBERS + 1 (left the team). */
    public static final int MEMBERS = 6;

    private AllocationScenarios() {}

    public enum Mode {
        ALL_TRUE_UP(20_261_026L),
        ALL_POOLED(20_262_026L),
        MIXED(20_263_026L);

        public final long baseSeed;

        Mode(final long baseSeed) {
            this.baseSeed = baseSeed;
        }

        boolean hasTrueUp() {
            return this != ALL_POOLED;
        }

        boolean hasPooled() {
            return this != ALL_TRUE_UP;
        }

        /** Always consumes one random bit, so the modes generate the same shapes. */
        LossPolicy policy(final Random rnd) {
            final boolean coin = rnd.nextBoolean();
            return switch (this) {
                case ALL_TRUE_UP -> LossPolicy.TRUE_UP;
                case ALL_POOLED -> LossPolicy.POOLED;
                case MIXED -> coin ? LossPolicy.TRUE_UP : LossPolicy.POOLED;
            };
        }
    }

    public static AllocationInput randomInput(final Random rnd, final Mode mode) {
        final int vesselCount = 1 + rnd.nextInt(8);
        final var ids = shuffledRange(rnd, 1, 40);
        final int statusMode = rnd.nextInt(6); // 0 = all open, 1 = all closed, else mixed

        // Index in `vessels` is the topological rank: transfers only go to higher ranks.
        final var vessels = new ArrayList<Vessel>();
        for (int rank = 0; rank < vesselCount; rank++) {
            final var status =
                    statusMode == 0
                            ? VesselStatus.IN_SERVICE
                            : statusMode == 1 || rnd.nextBoolean()
                                    ? VesselStatus.CLOSED
                                    : VesselStatus.IN_SERVICE;
            final var costLines = new ArrayList<CostLine>();
            for (int n = rnd.nextInt(4); n > 0; n--) {
                final long payer = 1L + rnd.nextInt(MEMBERS + 1); // member 7 has left the team
                final var amount =
                        rnd.nextInt(5) == 0
                                ? BigDecimal.ZERO
                                : BigDecimal.valueOf(rnd.nextInt(200_000), 2);
                costLines.add(new CostLine(payer, amount));
            }
            final double inflow = randomInflow(rnd);
            vessels.add(
                    new Vessel(
                            ids.get(rank),
                            mode.policy(rnd),
                            status,
                            costLines,
                            inflow,
                            randomResidual(rnd, inflow),
                            rnd.nextInt(3) == 0 ? List.of() : randomMembers(rnd)));
        }

        final var drawIds = shuffledRange(rnd, 100, 199);
        final var draws = new ArrayList<Draw>();
        for (int n = rnd.nextInt(31); n > 0; n--) {
            final int source = rnd.nextInt(vesselCount);
            final Beneficiary beneficiary;
            if (source < vesselCount - 1 && rnd.nextInt(10) < 4) {
                final int target = source + 1 + rnd.nextInt(vesselCount - source - 1);
                beneficiary = new ToVessel(vessels.get(target).vesselId());
            } else {
                beneficiary = new ToMember(1 + rnd.nextInt(MEMBERS));
            }
            draws.add(
                    new Draw(
                            drawIds.get(n),
                            vessels.get(source).vesselId(),
                            randomDrawLitres(rnd),
                            beneficiary));
        }

        final var active = rnd.nextInt(8) == 0 ? Set.<Long>of() : new HashSet<>(randomMembers(rnd));
        return new AllocationInput(vessels, draws, active);
    }

    /**
     * Independent oracle for {@code VesselAllocation.provisional} (DOMAIN §7.1): vessels that are
     * open TRUE_UP, or that a provisional vessel draws into, transitively. Plain DFS over the
     * input.
     */
    public static Set<Long> provisionalVessels(final AllocationInput input) {
        final var memo = new HashMap<Long, Boolean>();
        final var provisional = new TreeSet<Long>();
        for (final var vessel : input.vessels()) {
            if (isProvisional(vessel.vesselId(), input, memo)) {
                provisional.add(vessel.vesselId());
            }
        }
        return provisional;
    }

    private static boolean isProvisional(
            final long vesselId, final AllocationInput input, final Map<Long, Boolean> memo) {
        final var known = memo.get(vesselId);
        if (known != null) {
            return known;
        }
        final var vessel =
                input.vessels().stream()
                        .filter(v -> v.vesselId() == vesselId)
                        .findFirst()
                        .orElseThrow();
        boolean provisional =
                vessel.policy() == LossPolicy.TRUE_UP && vessel.status() == VesselStatus.IN_SERVICE;
        for (final var draw : input.draws()) {
            if (!provisional
                    && draw.beneficiary() instanceof ToVessel(final var targetId)
                    && targetId == vesselId) {
                provisional = isProvisional(draw.sourceVesselId(), input, memo);
            }
        }
        memo.put(vesselId, provisional);
        return provisional;
    }

    private static double randomInflow(final Random rnd) {
        return switch (rnd.nextInt(8)) {
            case 0 -> 0;
            case 1 -> 1 + rnd.nextInt(10_000);
            default -> 50 + rnd.nextDouble() * 9_950;
        };
    }

    private static double randomResidual(final Random rnd, final double inflow) {
        return switch (rnd.nextInt(6)) {
            case 0 -> 0;
            case 1 -> inflow; // V_in - V_res == 0
            case 2 -> inflow * (1 + rnd.nextDouble()); // V_res above V_in
            default -> inflow * rnd.nextDouble() * 0.5;
        };
    }

    private static double randomDrawLitres(final Random rnd) {
        return switch (rnd.nextInt(8)) {
            case 0 -> 0;
            case 1 -> rnd.nextInt(2_000);
            default -> rnd.nextDouble() * 600;
        };
    }

    private static List<Long> randomMembers(final Random rnd) {
        final var members = new ArrayList<Long>(shuffledRange(rnd, 1, MEMBERS));
        return members.subList(0, rnd.nextInt(MEMBERS + 1));
    }

    private static List<Long> shuffledRange(final Random rnd, final int from, final int to) {
        final var range = new ArrayList<Long>();
        for (long id = from; id <= to; id++) {
            range.add(id);
        }
        Collections.shuffle(range, rnd);
        return range;
    }
}
