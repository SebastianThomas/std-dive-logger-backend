package ch.sthomas.stddivelogger.model.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;

public class UnauthorizedException extends AbstractThrowableProblem {
    public UnauthorizedException(final String message) {
        super(URI.create("/problem/unauthorized"), "Unauthorized", HttpStatus.UNAUTHORIZED, message);
    }
}
