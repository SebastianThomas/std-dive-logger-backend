package ch.sthomas.stddivelogger.model.analytics;

public record AnalyticsDepthVarianceResponse(
        long diveId,
        long profileId,
        long profileSegmentId,
        int startIdx,
        int lastIdx,
        AnalyticsDepthVarianceStats stats) {}
