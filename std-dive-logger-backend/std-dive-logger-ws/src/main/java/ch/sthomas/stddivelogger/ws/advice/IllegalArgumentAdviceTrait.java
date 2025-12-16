package ch.sthomas.stddivelogger.ws.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

public interface IllegalArgumentAdviceTrait extends AdviceTrait {
    Logger logger = LoggerFactory.getLogger(IllegalArgumentAdviceTrait.class);

    @ExceptionHandler
    default ResponseEntity<Problem> handleIllegalArgumentOperation(
            final IllegalArgumentException exception, final NativeWebRequest request) {
        logger.warn(
                "IllegalArgumentException while handling request to {}.",
                request.getDescription(false),
                exception);
        return create(Status.BAD_REQUEST, exception, request);
    }
}
