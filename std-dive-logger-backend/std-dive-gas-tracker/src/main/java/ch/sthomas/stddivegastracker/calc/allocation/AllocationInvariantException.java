package ch.sthomas.stddivegastracker.calc.allocation;

/**
 * Inputs the service layer must never produce (a transfer cycle, a closed vessel nobody can share,
 * a lookup of an id that is not in the result): a bug, not a user error. A subtype of {@link
 * IllegalStateException}; the web layer answers it with 500 instead of a business 409/404.
 */
public final class AllocationInvariantException extends IllegalStateException {

    public AllocationInvariantException(final String message) {
        super(message);
    }
}
