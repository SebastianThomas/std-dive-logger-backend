package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * A dive count + total bottom time over one time window, from {@code t_dive_summary} only. {@code
 * bottomTime} is null only when {@code diveCount == 0}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeWindow(long diveCount, @Nullable Duration bottomTime) {
    public static final HomeWindow EMPTY = new HomeWindow(0, null);
}
