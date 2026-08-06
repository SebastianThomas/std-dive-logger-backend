package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDiveStats(
        long diveCount,
        long maxDiveNr,
        @Nullable Duration longestDive,
        double maxDepth,
        @Nullable Duration totalTime,
        @Nullable Long nrOfBuddies,
        @Nullable Long nrOfSites,
        @Nullable Temperature maxTemp,
        @Nullable Temperature minTemp)
        implements Comparable<UserDiveStats> {
    public UserDiveStats withBuddies(@Nullable final Long l) {
        return new UserDiveStats(
                diveCount,
                maxDiveNr,
                longestDive,
                maxDepth,
                totalTime,
                l,
                nrOfSites,
                maxTemp,
                minTemp);
    }

    public UserDiveStats withSites(@Nullable final Long l) {
        return new UserDiveStats(
                diveCount,
                maxDiveNr,
                longestDive,
                maxDepth,
                totalTime,
                nrOfBuddies,
                l,
                maxTemp,
                minTemp);
    }

    @Override
    public int compareTo(final UserDiveStats o) {
        final var count = Long.compare(diveCount, o.diveCount);
        if (count != 0) {
            return count;
        }
        if (totalTime != null && o.totalTime != null) {
            final var total = totalTime.compareTo(o.totalTime);
            if (total != 0) {
                return total;
            }
        }
        if (longestDive != null && o.longestDive != null) {
            final var longest = longestDive.compareTo(o.longestDive);
            if (longest != 0) {
                return longest;
            }
        }
        return Long.compare(maxDiveNr, o.maxDiveNr);
    }
}
