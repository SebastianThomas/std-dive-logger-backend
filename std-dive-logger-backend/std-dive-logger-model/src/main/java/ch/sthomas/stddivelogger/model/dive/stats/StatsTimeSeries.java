package ch.sthomas.stddivelogger.model.dive.stats;

import java.util.List;

public record StatsTimeSeries(
        List<StatsTimeSeriesPoint> points,
        /**
         * Same shape as {@code points}, one row per (bucket, category) when a breakdown dimension
         * was requested.
         */
        List<StatsTimeSeriesPoint> breakdown) {}
