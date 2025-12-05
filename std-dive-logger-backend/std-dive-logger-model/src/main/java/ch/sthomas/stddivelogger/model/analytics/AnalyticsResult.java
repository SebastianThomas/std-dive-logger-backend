package ch.sthomas.stddivelogger.model.analytics;

import java.util.List;
import java.util.stream.Stream;

public record AnalyticsResult(boolean successful, List<String> message) {
    public AnalyticsResult merge(final AnalyticsResult other) {
        return new AnalyticsResult(
                successful && other.successful,
                Stream.concat(message().stream(), other.message().stream()).toList());
    }
}
