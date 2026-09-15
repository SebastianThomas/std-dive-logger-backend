package ch.sthomas.stddivelogger.model.importfile;

public enum ReprocessConflictKind {
    /** Samples, clock or deco settings of one profile. */
    PROFILE,
    /** One dive-level value, see {@link ImportedDiveField}. */
    DIVE_FIELD
}
