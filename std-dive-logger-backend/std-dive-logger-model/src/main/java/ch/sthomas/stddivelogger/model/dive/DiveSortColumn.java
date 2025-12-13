package ch.sthomas.stddivelogger.model.dive;

public enum DiveSortColumn {
    ID,
    NUMBER,
    CUSTOM_IDENTIFIER;

    public static final DiveSortColumn DEFAULT = NUMBER;

    public static DiveSortColumn orDefault(final DiveSortColumn sort) {
        if (sort != null) {
            return sort;
        }
        return DEFAULT;
    }

    public String jpaName() {
        return switch (this) {
            case ID -> "id";
            case NUMBER -> "number";
            case CUSTOM_IDENTIFIER -> "diveIdentifier";
        };
    }
}
