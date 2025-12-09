package ch.sthomas.stddivelogger.model.exception;

public class UserCreationException extends RuntimeException {
    public UserCreationException(final String message) {
        super(message);
    }
}
