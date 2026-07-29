package ch.sthomas.stddivelogger.model.analytics;

import java.time.Instant;
import java.util.List;

public record DiveProfileRatesResponse(long profileId, List<RatePoint> rates) {
    public record RatePoint(Instant time, double depth, double rateMetersPerMinute) {}
}
