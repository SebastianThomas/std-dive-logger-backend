package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;

public record AnalyticsDepthVariance(
        DiveProfile profile,
        DiveMeasurementWithId measurementStart,
        DiveMeasurementWithId measurementEnd,
        long version,
        double avgDepth,
        double maxDepth,
        double minDepth,
        double deviationAvg,
        double deviationVariance,
        double deviation01p,
        double deviation10p,
        double deviationMedian,
        double deviation90p,
        double deviationMax) {}
