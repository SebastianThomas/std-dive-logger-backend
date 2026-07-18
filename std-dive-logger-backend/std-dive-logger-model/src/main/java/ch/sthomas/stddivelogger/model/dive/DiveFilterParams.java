package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record DiveFilterParams(
        @Nullable List<Long> tagIds,
        @Nullable Long diveSiteId,
        @Nullable Long suitId,
        @Nullable BaseConfiguration baseConfiguration,
        @Nullable String query,
        @Nullable Instant startDate,
        @Nullable Instant endDate,
        /** Time-of-day range (e.g. "morning dives"); matches when the dive's own start/end
         * window overlaps this range at all, not just when it's fully contained in it. */
        @Nullable LocalTime startTime,
        @Nullable LocalTime endTime) {}
