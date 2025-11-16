package ch.sthomas.stddivelogger.model.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(final String message) {
        super(message);
    }
}
