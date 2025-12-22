package ch.sthomas.stddivelogger.model.dive.stats;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;

import java.time.Duration;

public record UserDiveStats(
        long diveCount,
        long maxDiveNr,
        Duration longestDive,
        double maxDepth,
        Duration totalTime,
        long nrOfBuddies,
        long nrOfSites,
        Temperature maxTemp,
        Temperature minTemp) {}
