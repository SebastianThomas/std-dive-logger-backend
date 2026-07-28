package ch.sthomas.stddivelogger.utils.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

public interface IllegalArgumentAdviceTrait {
    Logger logger = LoggerFactory.getLogger(IllegalArgumentAdviceTrait.class);

    @ExceptionHandler(IllegalArgumentException.class)
    default ProblemDetail handleIllegalArgument(
            final IllegalArgumentException exception, final WebRequest request) {
        logger.warn(
                "IllegalArgumentException while handling request to {}.",
                request.getDescription(false),
                exception);
        final var detail = exception.getMessage() != null ? exception.getMessage() : "Bad request";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
