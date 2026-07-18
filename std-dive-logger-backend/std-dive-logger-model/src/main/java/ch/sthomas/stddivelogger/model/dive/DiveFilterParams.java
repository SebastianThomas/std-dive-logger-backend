package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

public record DiveFilterParams(
        @Nullable List<Long> tagIds,
        @Nullable Long diveSiteId,
        @Nullable Long suitId,
        @Nullable BaseConfiguration baseConfiguration,
        @Nullable String query,
        @Nullable Instant startDate,
        @Nullable Instant endDate) {}
