package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

public enum DiveSortColumn {
    ID,
    NUMBER,
    CUSTOM_IDENTIFIER,
    DATE;

    public static final DiveSortColumn DEFAULT = NUMBER;

    public static DiveSortColumn orDefault(@Nullable final DiveSortColumn sort) {
        if (sort != null) {
            return sort;
        }
        return DEFAULT;
    }

    /**
     * A JPA property path, for JPQL-backed sortable queries only - a native-query caller (see
     * {@code DiveRepository.findByGroupPrivilegeOrderByDiveStart}) can't use this directly, since
     * native sorting needs a raw SQL column name instead and can't reach a joined table's column
     * via {@code Pageable}'s sort machinery at all; that caller builds its own explicit {@code
     * ORDER BY} instead of going through this method.
     */
    public String jpaName() {
        return switch (this) {
            case ID -> "id";
            case NUMBER -> "number";
            case CUSTOM_IDENTIFIER -> "diveIdentifier";
            case DATE -> "diveSummary.start";
        };
    }
}
