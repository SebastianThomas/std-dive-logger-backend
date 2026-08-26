package ch.sthomas.stddivelogger.model.dive.stats;

import org.jspecify.annotations.Nullable;

// key is nullable for breakdowns where "not specified" is itself a real group (e.g.
// BaseConfiguration) - most other breakdowns COALESCE their SQL grouping column to a placeholder
// string instead, so key is non-null in practice for those.
public record UserDiveStatsBy<K>(@Nullable K key, UserDiveStats stats)
        implements Comparable<UserDiveStatsBy<K>> {
    @Override
    public int compareTo(final UserDiveStatsBy<K> o) {
        return stats.compareTo(o.stats);
    }
}
