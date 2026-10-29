package ch.sthomas.stddivegastracker.ws.advice;

import ch.sthomas.stddivegastracker.calc.allocation.AllocationInvariantException;

import jakarta.validation.ConstraintViolationException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Exception -> ProblemDetail, mirroring the dive logger's utils.advice (re-implemented: that one
 * references dive-specific exceptions). Services just throw; nothing catches-and-translates.
 * HttpMessageNotReadable/MethodArgumentNotValid must be @Overrides, not @ExceptionHandlers: the
 * superclass already claims them (an extra handler fails startup as "ambiguous").
 */
@RestControllerAdvice
public final class ExceptionHandling extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandling.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(final NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), "Not found.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(final IllegalArgumentException e) {
        log.info("Bad request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "Bad request.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(final ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "Bad request.");
    }

    /** A bug, not a conflict: logged in full, no internals for the client. */
    @ExceptionHandler(AllocationInvariantException.class)
    public ProblemDetail handleInvariant(final AllocationInvariantException e) {
        log.error("Allocation invariant violated", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, null, "Internal error.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(final IllegalStateException e) {
        log.info("Conflict: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, e.getMessage(), "Conflict.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(final DataIntegrityViolationException e) {
        // Never echo SQL/constraint details to the client.
        log.warn("Data integrity violation", e);
        return problem(HttpStatus.CONFLICT, null, "Conflicts with existing data.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleForbidden(final AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage(), "Forbidden.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            final HttpMessageNotReadableException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final var message = validationMessage(exception);
        log.info("Unreadable request body for {}: {}", request.getDescription(false), message);
        final var detail = problem(HttpStatus.BAD_REQUEST, message, "Malformed body.");
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException exception,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request) {
        final var message =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
        final var detail = problem(HttpStatus.BAD_REQUEST, message, "Invalid request.");
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    /** A record's compact-constructor IllegalArgumentException, if that's what broke parsing. */
    private static @Nullable String validationMessage(final Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException && cause.getMessage() != null) {
                return cause.getMessage();
            }
        }
        return null;
    }

    private static ProblemDetail problem(
            final HttpStatus status, final @Nullable String detail, final String fallback) {
        return ProblemDetail.forStatusAndDetail(
                status, detail == null || detail.isBlank() ? fallback : detail);
    }
}
