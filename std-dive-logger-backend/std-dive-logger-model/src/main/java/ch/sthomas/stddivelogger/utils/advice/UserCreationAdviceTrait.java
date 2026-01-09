package ch.sthomas.stddivelogger.utils.advice;

import ch.sthomas.stddivelogger.model.exception.UserCreationException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

public interface UserCreationAdviceTrait extends AdviceTrait {
    @ExceptionHandler
    default ResponseEntity<Problem> handleUserCreationException(
            final UserCreationException e, final NativeWebRequest request) {
        return create(Status.INTERNAL_SERVER_ERROR, e, request);
    }
}
