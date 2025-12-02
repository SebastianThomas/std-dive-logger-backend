package ch.sthomas.stddivelogger.model.exception;

public class DiveConstraintException extends RuntimeException {
    public DiveConstraintException(final String message, final Exception exception) {
        super(message, exception);
    }
}
