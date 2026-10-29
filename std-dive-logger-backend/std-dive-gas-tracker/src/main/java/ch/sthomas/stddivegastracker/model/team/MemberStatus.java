package ch.sthomas.stddivegastracker.model.team;

/**
 * DOMAIN §2 Member status; derived (left, invited-without-user, else active), never stored. The
 * declaration order is the list order of API.md §2.
 */
public enum MemberStatus {
    ACTIVE,
    PENDING,
    LEFT
}
