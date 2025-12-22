package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;

public record AnalyticsDepthVariance(
        DiveProfileSegmentWithId segmentWithId, AnalyticsDepthVarianceStats stats) {}
