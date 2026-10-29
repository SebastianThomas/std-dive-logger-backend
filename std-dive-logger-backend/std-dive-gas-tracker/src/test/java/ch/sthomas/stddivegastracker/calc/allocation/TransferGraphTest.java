package ch.sthomas.stddivegastracker.calc.allocation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivegastracker.calc.allocation.TransferGraph.Edge;

import org.junit.jupiter.api.Test;

import java.util.List;

/** DOMAIN §5.6: a fill from S into T may not close a loop in the transfer graph. */
final class TransferGraphTest {

    private static final String LOOP = "This would move gas in a loop between banks.";

    // O2 (1) -> EAN32 (2) -> EAN28 (3), and an unrelated He (4)
    private static final List<Edge> CHAIN = List.of(new Edge(1, 2), new Edge(2, 3));

    @Test
    void aSourceThatIsTheTargetIsALoop() {
        assertThatThrownBy(() -> TransferGraph.requireNoLoop(List.of(), 2, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOOP);
    }

    @Test
    void aSourceTheTargetsGasAlreadyReachedIsALoopDirectlyOrThroughTheChain() {
        for (final var sourceId : List.of(2L, 3L)) {
            assertThatThrownBy(() -> TransferGraph.requireNoLoop(CHAIN, 1, List.of(sourceId)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(LOOP);
        }
        // one bad source among good ones is enough
        assertThatThrownBy(() -> TransferGraph.requireNoLoop(CHAIN, 2, List.of(4L, 3L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LOOP);
    }

    @Test
    void sourcesUpstreamOrUnrelatedAreFine() {
        assertThatCode(() -> TransferGraph.requireNoLoop(CHAIN, 3, List.of(1L, 2L, 4L)))
                .doesNotThrowAnyException();
        assertThatCode(() -> TransferGraph.requireNoLoop(CHAIN, 4, List.of(1L, 3L)))
                .doesNotThrowAnyException();
        assertThatCode(() -> TransferGraph.requireNoLoop(List.of(), 1, List.of(2L)))
                .doesNotThrowAnyException();
    }

    @Test
    void aCycleAlreadyInTheEdgesTerminates() {
        // never stored (it is what the check prevents), but the walk must not spin on it
        final var cycle = List.of(new Edge(1, 2), new Edge(2, 1));

        assertThatCode(() -> TransferGraph.requireNoLoop(cycle, 1, List.of(9L)))
                .doesNotThrowAnyException();
    }
}
