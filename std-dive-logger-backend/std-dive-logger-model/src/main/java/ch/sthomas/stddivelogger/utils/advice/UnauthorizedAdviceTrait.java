package ch.sthomas.stddivelogger.utils.advice;

import ch.sthomas.stddivelogger.model.exception.InvalidPasswordException;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

public interface UnauthorizedAdviceTrait extends AdviceTrait {

    @ExceptionHandler
    default ResponseEntity<Problem> handleConstraintException(
            final UnauthorizedException exception, final NativeWebRequest request) {
        return create(Status.UNAUTHORIZED, exception, request);
    }

    @ExceptionHandler
    default ResponseEntity<Problem> handleBadCredentialsException(
            final BadCredentialsException ex, final NativeWebRequest request) {
        return create(Status.UNAUTHORIZED, ex, request);
    }

    @ExceptionHandler
    default ResponseEntity<Problem> handleInvalidPasswordException(
            final InvalidPasswordException ex, final NativeWebRequest request) {
        return create(
                Problem.builder()
                        .withStatus(Status.BAD_REQUEST)
                        .withTitle(ex.getMessage())
                        .withDetail(String.join(",\n", ex.details()))
                        .build(),
                request);
    }

    @ExceptionHandler
    default ResponseEntity<Problem> handleDisabledException(
            final DisabledException ex, final NativeWebRequest request) {
        return create(Problem.valueOf(Status.FORBIDDEN, "User disabled"), request);
    }
}
