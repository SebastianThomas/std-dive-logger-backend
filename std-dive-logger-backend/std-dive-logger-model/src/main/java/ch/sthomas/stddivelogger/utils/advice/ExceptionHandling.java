package ch.sthomas.stddivelogger.utils.advice;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ExceptionHandling extends ResponseEntityExceptionHandler
        implements ThrowableProblemAdviceTrait,
                NoSuchElementAdviceTrait,
                IllegalArgumentAdviceTrait,
                IllegalStateAdviceTrait,
                UncheckedIOAdviceTrait,
                ConstraintViolationAdviceTrait,
                DiveDBConstraintAdviceTrait,
                HttpMessageNotReadableAdviceTrait,
                SecurityAdviceTrait {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandling.class);

    // See HttpMessageNotReadableAdviceTrait's own doc for why this must be a real @Override here
    // rather than a plain @ExceptionHandler default method like every other trait in this package.
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        logger.warn(
                "Could not read request body for {}.", request.getDescription(false), exception);
        final var problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.valueOf(status.value()),
                        HttpMessageNotReadableAdviceTrait.extractDetail(exception));
        return handleExceptionInternal(exception, problemDetail, headers, status, request);
    }
}
