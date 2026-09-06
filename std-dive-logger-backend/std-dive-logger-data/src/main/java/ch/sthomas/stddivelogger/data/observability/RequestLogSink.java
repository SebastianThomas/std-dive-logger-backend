package ch.sthomas.stddivelogger.data.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Precorrelation;
import org.zalando.logbook.Sink;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class RequestLogSink implements Sink {
    static final Logger LOGGER = LoggerFactory.getLogger("org.zalando.logbook.Logbook");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> SAFE_FIELDS =
            Set.of("page", "size", "count", "limit", "offset", "enabled", "success");

    @Override
    public void write(final Precorrelation correlation, final HttpRequest request)
            throws IOException {
        LOGGER.atInfo()
                .addKeyValue("log_type", "http_request")
                .addKeyValue("correlation_id", correlation.getId())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getPath())
                .addKeyValue("request_body", preview(request.getBodyAsString()))
                .log("HTTP request received");
    }

    @Override
    public void write(
            final Correlation correlation, final HttpRequest request, final HttpResponse response)
            throws IOException {
        final var status = response.getStatus();
        LOGGER.atLevel(level(status))
                .addKeyValue("log_type", "http_access")
                .addKeyValue("correlation_id", correlation.getId())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getPath())
                .addKeyValue("status", status)
                .addKeyValue("duration_ms", correlation.getDuration().toNanos() / 1_000_000.0)
                .log("HTTP request completed");
    }

    static Level level(final int status) {
        return status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
    }

    static Map<String, Object> preview(final String body) {
        if (body.isEmpty() || body.length() > SafeBodyStrategy.MAX_BODY_BYTES) {
            return Map.of();
        }
        try {
            final var node = JSON.readTree(body);
            final Map<String, Object> result = new TreeMap<>();
            for (final var field : SAFE_FIELDS) {
                final var value = node.get(field);
                if (value != null && (value.isNumber() || value.isBoolean())) {
                    result.put(
                            field, value.isBoolean() ? value.booleanValue() : value.numberValue());
                }
            }
            return result;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
