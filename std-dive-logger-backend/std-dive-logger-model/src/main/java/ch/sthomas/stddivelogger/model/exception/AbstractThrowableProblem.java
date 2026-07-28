package ch.sthomas.stddivelogger.model.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.Map;

/**
 * Base class for domain exceptions that carry their own RFC 7807 problem representation. {@link
 * ch.sthomas.stddivelogger.utils.advice.ExceptionHandling} handles every subclass generically via
 * {@link #toProblemDetail()}.
 */
public abstract class AbstractThrowableProblem extends RuntimeException {
    private final URI type;
    private final String title;
    private final HttpStatusCode status;
    private final String detail;

    protected AbstractThrowableProblem(
            final URI type, final String title, final HttpStatusCode status, final String detail) {
        super(title + ": " + detail);
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
    }

    protected AbstractThrowableProblem(
            final URI type,
            final String title,
            final HttpStatusCode status,
            final String detail,
            final Throwable cause) {
        super(title + ": " + detail, cause);
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
    }

    public Map<String, Object> additionalProperties() {
        return Map.of();
    }

    public ProblemDetail toProblemDetail() {
        final var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(type);
        problemDetail.setTitle(title);
        problemDetail.setProperties(additionalProperties());
        return problemDetail;
    }
}
