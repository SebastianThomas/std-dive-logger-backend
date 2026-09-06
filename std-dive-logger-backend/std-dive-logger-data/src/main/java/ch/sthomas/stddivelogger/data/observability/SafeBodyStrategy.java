package ch.sthomas.stddivelogger.data.observability;

import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Strategy;

import java.io.IOException;
import java.util.Locale;

final class SafeBodyStrategy implements Strategy {
    static final int MAX_BODY_BYTES = 2048;

    @Override
    public HttpRequest process(final HttpRequest request) throws IOException {
        final var contentType = request.getContentType();
        if (contentType == null) return request.withoutBody();
        final var type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0];
        final var length = request.getHeaders().getFirst("Content-Length");
        try {
            if (length != null
                    && Long.parseLong(length) > 0
                    && Long.parseLong(length) <= MAX_BODY_BYTES
                    && (type.equals("application/json") || type.endsWith("+json"))
                    && request.getHeaders().getFirst("Content-Encoding") == null
                    && request.getHeaders().getFirst("Transfer-Encoding") == null) {
                return request.withBody();
            }
        } catch (NumberFormatException ignored) {
            // Unknown sizes are never buffered for logging.
        }
        return request.withoutBody();
    }

    @Override
    public HttpResponse process(final HttpRequest request, final HttpResponse response) {
        // Response type/size is unknown before the handler runs. Never buffer downloads or SSE.
        return response.withoutBody();
    }
}
