package ch.sthomas.stddivelogger.model.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;

public class UserCreationException extends AbstractThrowableProblem {
    public UserCreationException(final String message) {
        super(
                URI.create("/problem/user-creation"),
                "User Creation Failed",
                HttpStatus.INTERNAL_SERVER_ERROR,
                message);
    }
}
