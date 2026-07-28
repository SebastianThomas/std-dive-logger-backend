package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

public record TagDefinition(
        long id,
        String name,
        /** Null means this is a user-created tag with no auto-detection. */
        @Nullable AutoDetectRule autoDetectRule,
        /** Null means this is a system-wide default tag. */
        @Nullable Long userId,
        /** Number of the current user's dives that carry this tag (excluding dismissed rows). */
        long diveCount) {}
