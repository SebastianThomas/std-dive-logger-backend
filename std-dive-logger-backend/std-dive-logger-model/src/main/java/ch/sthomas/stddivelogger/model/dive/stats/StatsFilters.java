package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;

import jakarta.annotation.Nullable;

import java.util.List;

public record StatsFilters(
        @Nullable List<Long> tagIds,
        @Nullable Long diveSiteId,
        @Nullable Long suitId,
        @Nullable Long ccrUnitId,
        @Nullable BaseConfiguration baseConfiguration,
        @Nullable String query) {
    public static final StatsFilters EMPTY = new StatsFilters(null, null, null, null, null, null);
}
