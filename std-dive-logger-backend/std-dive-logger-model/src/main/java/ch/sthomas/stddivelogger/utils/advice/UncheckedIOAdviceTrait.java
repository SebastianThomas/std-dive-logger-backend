package ch.sthomas.stddivelogger.utils.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.io.UncheckedIOException;

/**
 * An I/O failure (e.g. a client disconnect or truncated upload mid-request, or a genuine
 * server-side storage problem) still isn't something the client can retry their way out of, so this
 * stays a 500 rather than a 4xx - but without this handler it was an *uncontrolled* 500, leaking
 * the exception's message and stack trace straight to the client instead of a clean, generic
 * problem response (with the actual detail still logged server-side).
 */
public interface UncheckedIOAdviceTrait {
    Logger logger = LoggerFactory.getLogger(UncheckedIOAdviceTrait.class);

    @ExceptionHandler(UncheckedIOException.class)
    default ProblemDetail handleUncheckedIO(
            final UncheckedIOException exception, final WebRequest request) {
        logger.error(
                "UncheckedIOException while handling request to {}.",
                request.getDescription(false),
                exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not process the request due to an I/O error.");
    }
}
