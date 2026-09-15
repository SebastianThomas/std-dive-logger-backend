package ch.sthomas.stddivelogger.model.importfile;

import java.util.List;

/** One stored file of a dive: which of its profiles and which dive-level values came from it. */
public record DiveSourceFile(
        ImportFileInfo file, List<Long> profileIds, List<ImportedDiveField> fields) {}
