package ch.sthomas.stddivelogger.model.exception;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsResult;

public class AnalyticsException extends RuntimeException {
    public AnalyticsException(final String message) {
        super(message);
    }

    public AnalyticsException(final AnalyticsResult result) {
        super(String.join("\n", result.message()));
    }
}
