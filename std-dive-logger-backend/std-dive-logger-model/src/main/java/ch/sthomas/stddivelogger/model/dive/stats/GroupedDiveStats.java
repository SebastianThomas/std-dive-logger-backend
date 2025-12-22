package ch.sthomas.stddivelogger.model.dive.stats;

public record GroupedDiveStats<T>(T groupKey, UserDiveStats stats) {}
