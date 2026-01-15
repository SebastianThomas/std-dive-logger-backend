package ch.sthomas.stddivelogger.model.dive.stats;

public record UserDiveStatsBy<K>(K key, UserDiveStats stats)
        implements Comparable<UserDiveStatsBy<K>> {
    @Override
    public int compareTo(final UserDiveStatsBy<K> o) {
        return stats.compareTo(o.stats);
    }
}
