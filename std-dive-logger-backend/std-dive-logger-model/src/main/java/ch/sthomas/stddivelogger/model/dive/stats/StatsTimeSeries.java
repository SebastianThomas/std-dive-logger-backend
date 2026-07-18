package ch.sthomas.stddivelogger.model.dive.stats;

import java.util.List;

public record StatsTimeSeries(
        List<StatsTimeSeriesPoint> points,
        List<StatsCategoryPoint> suitUsage,
        List<StatsCategoryPoint> baseConfigurationUsage) {}
