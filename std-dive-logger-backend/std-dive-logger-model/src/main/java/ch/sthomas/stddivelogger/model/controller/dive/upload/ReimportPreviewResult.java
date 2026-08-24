package ch.sthomas.stddivelogger.model.controller.dive.upload;

/**
 * Result of staging a reimport-in-place upload (already passed the "is this really the same dive"
 * check - see {@code ReimportSimilarityCheck} - a mismatch throws instead of returning a result
 * here). If {@code conflicts.hasAny()} is false, the caller can commit immediately with an all-null
 * {@link ReimportResolution}; otherwise show the conflicting fields for the user to pick.
 */
public record ReimportPreviewResult(long pendingImportId, ReimportConflicts conflicts) {}
