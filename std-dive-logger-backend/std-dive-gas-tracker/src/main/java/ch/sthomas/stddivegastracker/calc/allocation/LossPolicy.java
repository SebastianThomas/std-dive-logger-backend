package ch.sthomas.stddivegastracker.calc.allocation;

/** How a vessel's cost is shared (DOMAIN §7.3). */
public enum LossPolicy {
    /** Every vessel's full cost is shared by its draws, pro-rata to litres. */
    TRUE_UP,
    /** Draws pay the nominal price; whatever was not drawn is a team loss. */
    POOLED
}
