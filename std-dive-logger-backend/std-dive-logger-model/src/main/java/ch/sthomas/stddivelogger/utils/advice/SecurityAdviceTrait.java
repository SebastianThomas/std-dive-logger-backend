package ch.sthomas.stddivelogger.utils.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;

public interface SecurityAdviceTrait {

    @ExceptionHandler(DisabledException.class)
    default ProblemDetail handleDisabledException(final DisabledException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "User disabled");
    }

    @ExceptionHandler(AccessDeniedException.class)
    default ProblemDetail handleAccessDenied(final AccessDeniedException exception) {
        final var detail = exception.getMessage() != null ? exception.getMessage() : "Forbidden";
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
    }

    @ExceptionHandler(AuthenticationException.class)
    default ProblemDetail handleAuthenticationException(final AuthenticationException exception) {
        final var detail =
                exception.getMessage() != null ? exception.getMessage() : "Unauthorized";
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
    }
}
