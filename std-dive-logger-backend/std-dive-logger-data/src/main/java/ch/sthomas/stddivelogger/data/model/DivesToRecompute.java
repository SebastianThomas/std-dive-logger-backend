package ch.sthomas.stddivelogger.data.model;

import ch.sthomas.stddivelogger.model.dive.Dive;

import java.util.List;
import java.util.Map;

/**
 * @param generations each dive's {@code t_dives.analytics_generation} when it was read - handed
 *     back to {@code AnalyticsDataService#recordJobStateIfUnchanged} so a dive changed meanwhile is
 *     not recorded as computed
 */
public record DivesToRecompute(List<Dive> dives, Map<Long, Long> generations, boolean hasMore) {

    public long generationOf(final long diveId) {
        return generations.getOrDefault(diveId, 0L);
    }
}
