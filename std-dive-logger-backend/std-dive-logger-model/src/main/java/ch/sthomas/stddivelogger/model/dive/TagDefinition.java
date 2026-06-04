package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

public record TagDefinition(
        long id,
        String name,
        /** Null means this is a user-created tag with no auto-detection. */
        @Nullable AutoDetectRule autoDetectRule,
        /** Null means this is a system-wide default tag. */
        @Nullable Long userId) {}
