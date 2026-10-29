package ch.sthomas.stddivegastracker.model.fill;

/** API.md §4 fill status; VOIDED fills stay listed but count nowhere (DOMAIN §5.6). */
public enum FillStatus {
    ACTIVE,
    VOIDED
}
