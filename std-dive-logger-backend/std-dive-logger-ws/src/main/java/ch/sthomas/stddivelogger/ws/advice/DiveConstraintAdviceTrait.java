package ch.sthomas.stddivelogger.ws.advice;

import ch.sthomas.stddivelogger.model.exception.DiveConstraintException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

public interface DiveConstraintAdviceTrait extends AdviceTrait {

    @ExceptionHandler
    default ResponseEntity<Problem> handleConstraintException(
            final DiveConstraintException exception, final NativeWebRequest request) {
        return create(Status.BAD_REQUEST, exception, request);
    }
}
