package ch.sthomas.stddivelogger.utils.advice;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

public interface ConstraintViolationAdviceTrait {

    @ExceptionHandler(ConstraintViolationException.class)
    default ProblemDetail handleConstraintViolation(final ConstraintViolationException exception) {
        final var body =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Constraint violation");
        final var violations =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new Violation(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                        .toList();
        body.setProperty("violations", violations);
        return body;
    }

    record Violation(String field, String message) {}
}
