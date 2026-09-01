package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record DiveFilterParams(
        @Nullable List<Long> tagIds,
        @Nullable Long diveSiteId,
        @Nullable Long suitId,
        @Nullable Long ccrUnitId,
        @Nullable BaseConfiguration baseConfiguration,
        @Nullable String query,
        @Nullable Instant startDate,
        @Nullable Instant endDate,
        /**
         * Time-of-day range (e.g. "morning dives"); matches when the dive's own start/end window
         * overlaps this range at all, not just when it's fully contained in it.
         */
        @Nullable LocalTime startTime,
        @Nullable LocalTime endTime,
        /** Inclusive dive-number range (e.g. "my dives 120-126") - either bound alone is fine. */
        @Nullable Integer minNumber,
        @Nullable Integer maxNumber,
        /** {@code TRUE} keeps only highlighted ('starred') dives; null/false doesn't filter. */
        @Nullable Boolean highlighted) {}
