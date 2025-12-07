package ch.sthomas.stddivelogger.model.analytics;

public record AnalyticsDepthVarianceResponse(
        long diveId, long profileId, int startIdx, AnalyticsDepthVarianceStats stats) {}
