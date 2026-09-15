package ch.sthomas.stddivelogger.model.importfile;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A stored upload as the frontend lists it. */
public record ImportFileInfo(
        long id,
        PendingImportSource source,
        @Nullable String originalFilename,
        String contentType,
        long sizeBytes,
        int diveCount,
        int profileCount,
        ImportFileScope scope,
        Instant createdAt,
        Instant updatedAt) {}
