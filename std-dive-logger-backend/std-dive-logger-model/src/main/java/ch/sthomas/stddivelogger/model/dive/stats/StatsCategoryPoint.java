package ch.sthomas.stddivelogger.model.dive.stats;

import java.time.Instant;

public record StatsCategoryPoint(Instant bucketStart, String category, long diveCount) {}
