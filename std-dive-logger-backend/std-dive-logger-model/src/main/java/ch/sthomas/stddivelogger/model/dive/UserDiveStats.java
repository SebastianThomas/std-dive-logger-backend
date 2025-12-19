package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;

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
