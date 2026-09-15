package ch.sthomas.stddivelogger.model.importfile;

/** The account's opt-in plus what it currently has stored. */
public record ImportFileSettings(boolean keepImportFiles, long fileCount, long totalBytes) {}
