package ch.sthomas.stddivelogger.model.exception;

public class DiveDBConstraintException extends RuntimeException {
    private final String message;

    public DiveDBConstraintException(final String message, final Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public String externalMessage() {
        return message;
    }
}
