package ch.sthomas.stddivegastracker.ws.advice;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInvariantException;

import org.junit.jupiter.api.Test;

/** A broken calc invariant is a bug: 500 without internals, never a business 409. */
final class ExceptionHandlingTest {

    private final ExceptionHandling handling = new ExceptionHandling();

    @Test
    void anAllocationInvariantIsAnInternalErrorWithoutDetails() {
        final var problem =
                handling.handleInvariant(new AllocationInvariantException("No draw 123"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("Internal error.");
    }

    @Test
    void aBusinessConflictStaysAConflictWithItsMessage() {
        final var problem = handling.handleConflict(new IllegalStateException("Bank is closed."));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Bank is closed.");
    }
}
