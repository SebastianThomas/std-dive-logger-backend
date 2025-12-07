package ch.sthomas.stddivelogger.model.analytics;

public record AnalyticsDepthVarianceStats(
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
        double deviationMax) {
    public AnalyticsDepthVarianceStats(final long version, final double depth) {
        this(version, depth, depth, depth, 0, 0, 0, 0, 0, 0, 0);
    }
}
