package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserDiveStats(
        long diveCount,
        long maxDiveNr,
        Duration longestDive,
        double maxDepth,
        Duration totalTime,
        Long nrOfBuddies,
        Long nrOfSites,
        Temperature maxTemp,
        Temperature minTemp) {
    public UserDiveStats withBuddies(final Long l) {
        return new UserDiveStats(
                diveCount,
                maxDiveNr,
                longestDive,
                maxDepth,
                totalTime,
                l,
                nrOfSites,
                maxTemp,
                maxTemp);
    }

    public UserDiveStats withSites(final Long l) {
        return new UserDiveStats(
                diveCount,
                maxDiveNr,
                longestDive,
                maxDepth,
                totalTime,
                nrOfBuddies,
                l,
                maxTemp,
                maxTemp);
    }
}
