package ch.sthomas.stddivegastracker.calc.allocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The transfer graph (DOMAIN §5.6, §7.4): vessels are nodes, gas moved from S into T is an edge S
 * -> T. It must stay a DAG, or the allocation engine throws for the whole team.
 */
public final class TransferGraph {

    private TransferGraph() {}

    /** Gas moved from one vessel into another by a (non-voided) fill. */
    public record Edge(long fromVesselId, long toVesselId) {}

    /**
     * Throws (400) if a fill from {@code sourceVesselIds} into {@code targetVesselId} would close a
     * loop: a source is the target, or the target's gas already reached it through {@code edges}.
     */
    public static void requireNoLoop(
            final Collection<Edge> edges,
            final long targetVesselId,
            final Collection<Long> sourceVesselIds) {
        final var downstream = new HashMap<Long, List<Long>>();
        for (final var edge : edges) {
            downstream
                    .computeIfAbsent(edge.fromVesselId(), _ -> new ArrayList<>())
                    .add(edge.toVesselId());
        }
        final var reached = new HashSet<Long>();
        final var pending = new ArrayDeque<Long>(List.of(targetVesselId));
        while (!pending.isEmpty()) {
            final var vesselId = pending.pop();
            if (reached.add(vesselId)) {
                pending.addAll(downstream.getOrDefault(vesselId, List.of()));
            }
        }
        if (sourceVesselIds.stream().anyMatch(reached::contains)) {
            throw new IllegalArgumentException("This would move gas in a loop between banks.");
        }
    }
}
