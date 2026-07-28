package ch.sthomas.stddivelogger.utils.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

public interface NoSuchElementAdviceTrait {

    @ExceptionHandler(NoSuchElementException.class)
    default ProblemDetail handleNoSuchElement(final NoSuchElementException exception) {
        final var detail = exception.getMessage() != null ? exception.getMessage() : "Not found";
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
    }
}
