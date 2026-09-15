package ch.sthomas.stddivelogger.model.importfile;

public enum ReprocessConflictStatus {
    OPEN,
    APPLIED,
    KEPT_CURRENT,
    /** Replaced by a newer run, or the data changed underneath it. */
    SUPERSEDED
}
