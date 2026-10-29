package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.model.vessel.Limits;

/** API.md §4 {@code POST …/void} body: a reason of 1-200 characters (DOMAIN §5.6). */
public record VoidFillRequest(String reason) {

    private static final int MAX_REASON_LENGTH = 200;

    public VoidFillRequest {
        final var stripped = Limits.text(reason, MAX_REASON_LENGTH, "Reason");
        if (stripped == null) {
            throw new IllegalArgumentException("A reason is required.");
        }
        reason = stripped;
    }
}
