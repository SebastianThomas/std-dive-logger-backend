package ch.sthomas.stddivelogger.model.importfile;

import java.util.EnumSet;
import java.util.Set;

/** A dive-level value a commit or refine took from a file. */
public enum ImportedDiveField {
    NOTES,
    VISIBILITY,
    BUDDIES,
    GAS_CONSUMPTION,
    CONFIGURATION,
    SITE,
    DIVE_NUMBER,
    DIVE_IDENTIFIER;

    /** Fields re-processing may update; the others identify the dive and are only recorded. */
    public static final Set<ImportedDiveField> REPROCESSABLE =
            EnumSet.of(NOTES, VISIBILITY, BUDDIES, GAS_CONSUMPTION);
}
