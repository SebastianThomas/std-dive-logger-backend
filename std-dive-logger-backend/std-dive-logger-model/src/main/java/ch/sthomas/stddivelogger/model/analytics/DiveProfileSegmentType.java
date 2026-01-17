package ch.sthomas.stddivelogger.model.analytics;

public enum DiveProfileSegmentType {
    SURFACE,
    DESCENT,
    LIGHT_DESCENT,
    HOLD_LEVEL,
    LIGHT_ASCENT,
    ASCENT,
    UNKNOWN;

    public boolean isAscent() {
        return switch (this) {
            case ASCENT, LIGHT_ASCENT -> true;
            default -> false;
        };
    }

    public boolean isDescent() {
        return switch (this) {
            case DESCENT, LIGHT_DESCENT -> true;
            default -> false;
        };
    }
}
