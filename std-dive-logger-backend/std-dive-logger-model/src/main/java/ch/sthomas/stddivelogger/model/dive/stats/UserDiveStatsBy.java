package ch.sthomas.stddivelogger.model.dive.stats;

public record UserDiveStatsBy<K>(K key, UserDiveStats stats) {}
