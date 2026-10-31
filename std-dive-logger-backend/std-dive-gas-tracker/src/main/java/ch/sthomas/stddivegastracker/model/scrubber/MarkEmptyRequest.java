package ch.sthomas.stddivegastracker.model.scrubber;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** API.md §7. An absent time, or no body at all, is now. */
public record MarkEmptyRequest(@Nullable Instant emptiedAt) {}
