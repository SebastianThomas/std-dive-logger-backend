package ch.sthomas.stddivelogger.model.exception;

import java.util.List;

public class InvalidPasswordException extends RuntimeException {
    private final List<String> details;

    public InvalidPasswordException(final List<String> details) {
        super("Invalid password");
        this.details = details;
    }

    public List<String> details() {
        return details;
    }
}
