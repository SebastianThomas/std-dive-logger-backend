package ch.sthomas.stddivelogger.utils.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Every {@link IllegalStateException} thrown in this codebase represents "the request can't be
 * fulfilled given the current data/state" (e.g. a FIT file with no computer serial to attach
 * measurements to, or two profiles picked for auto-align that don't share any overlapping time
 * range) - a client-triggerable, correctable condition, not a genuine server fault. Without this
 * handler these were surfacing as raw 500s with a stack trace leaked to the client.
 */
public interface IllegalStateAdviceTrait {
    Logger logger = LoggerFactory.getLogger(IllegalStateAdviceTrait.class);

    @ExceptionHandler(IllegalStateException.class)
    default ProblemDetail handleIllegalState(
            final IllegalStateException exception, final WebRequest request) {
        logger.warn(
                "IllegalStateException while handling request to {}.",
                request.getDescription(false),
                exception);
        final var detail = exception.getMessage() != null ? exception.getMessage() : "Bad request";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
