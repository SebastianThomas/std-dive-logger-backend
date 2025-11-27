package ch.sthomas.stddivelogger.ws.advice;

import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.AdviceTrait;

import java.util.NoSuchElementException;

public interface NoSuchElementAdviceTrait extends AdviceTrait {

    @ExceptionHandler
    default ResponseEntity<Problem> handleUnsupportedOperation(
            final NoSuchElementException exception, final NativeWebRequest request) {
        final var logger = LoggerFactory.getLogger(NoSuchElementAdviceTrait.class);
        logger.info("Not found", exception);
        return create(Status.NOT_FOUND, exception, request);
    }
}
