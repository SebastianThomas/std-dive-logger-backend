package ch.sthomas.stddivelogger.model.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

public class InvalidPasswordException extends AbstractThrowableProblem {
    private final List<String> details;

    public InvalidPasswordException(final List<String> details) {
        super(
                URI.create("/problem/invalid-password"),
                "Invalid password",
                HttpStatus.BAD_REQUEST,
                String.join(",\n", details));
        this.details = details;
    }

    public List<String> details() {
        return details;
    }
}
