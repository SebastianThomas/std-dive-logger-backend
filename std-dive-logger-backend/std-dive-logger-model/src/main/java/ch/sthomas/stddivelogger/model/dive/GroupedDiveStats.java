package ch.sthomas.stddivelogger.model.dive;

public record GroupedDiveStats<T>(T groupKey, UserDiveStats stats) {}
