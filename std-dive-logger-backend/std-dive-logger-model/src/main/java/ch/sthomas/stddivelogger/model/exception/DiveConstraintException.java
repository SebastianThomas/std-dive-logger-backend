package ch.sthomas.stddivelogger.model.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;

public class DiveConstraintException extends AbstractThrowableProblem {
    public DiveConstraintException(final String message, final Exception exception) {
        super(
                URI.create("/problem/dive-constraint"),
                "Dive Constraint Violation",
                HttpStatus.BAD_REQUEST,
                message,
                exception);
    }
}
