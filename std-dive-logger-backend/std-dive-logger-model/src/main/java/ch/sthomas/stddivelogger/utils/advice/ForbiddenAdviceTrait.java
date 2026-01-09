package ch.sthomas.stddivelogger.utils.advice;

import ch.sthomas.stddivelogger.model.exception.ForbiddenException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

public interface ForbiddenAdviceTrait extends AdviceTrait {
    @ExceptionHandler
    default ResponseEntity<Problem> handleUnsupportedOperation(
            final ForbiddenException exception, final NativeWebRequest request) {
        return create(Status.FORBIDDEN, exception, request);
    }
}
