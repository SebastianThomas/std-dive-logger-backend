package ch.sthomas.stddivelogger.model.dive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** Country and region derived from imported administrative boundaries. */
public record DerivedSiteLocation(
        long diveSiteId,
        @Nullable String countryName,
        @Nullable String countryCode,
        @Nullable String regionName,
        @Nullable String regionCode,
        String status,
        Instant resolvedAt,
        @Nullable Long boundaryImportVersion) {}
