package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.DiveProfile;

public record AnalyticsDepthVariance(
        DiveProfile profile,
        DiveMeasurementWithId measurementStart,
        DiveMeasurementWithId measurementEnd,
        int startIdx,
        AnalyticsDepthVarianceStats stats) {}
