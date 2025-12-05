package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;

public record AnalyticsDepthVariance(
        DiveProfile profile,
        DiveMeasurementWithId measurementStart,
        DiveMeasurementWithId measurementEnd,
        Double avgDepth,
        Double maxDepth,
        Double minDepth,
        Double deviationAvg,
        Double deviationVariance,
        Double deviation01p,
        Double deviation10p,
        Double deviationMedian,
        Double deviation90p,
        Double deviationMax) {}
