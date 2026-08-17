package ch.sthomas.stddivelogger.model.analytics;

import org.jspecify.annotations.Nullable;

/**
 * Gas-consumption figures computed from a dive's tracked {@code DiveConfigurationCylinder}s -
 * deliberately not a single "RMV" number, since that concept doesn't apply uniformly across OC and
 * CCR dives (see {@link CylinderConsumptionCalculator}'s own doc comment). Every field is {@code
 * null}, not zero, when there was nothing to compute it from (e.g. no OC-role cylinders tracked on
 * this dive) - a dive with no data for a figure must not silently read as "0", the same reasoning
 * behind the {@code StatsDataService} timeseries RMV fix this figure is meant to feed cleanly into.
 */
public record CylinderConsumptionResult(
        @Nullable Double ocRmvLiters,
        @Nullable Double bailoutRmvLiters,
        @Nullable Double o2Liters,
        @Nullable Double diluentLiters) {
    public static final CylinderConsumptionResult EMPTY =
            new CylinderConsumptionResult(null, null, null, null);
}
