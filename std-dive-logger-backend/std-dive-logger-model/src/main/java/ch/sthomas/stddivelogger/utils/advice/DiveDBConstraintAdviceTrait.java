package ch.sthomas.stddivelogger.utils.advice;

import ch.sthomas.stddivelogger.model.exception.DiveDBConstraintException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

public interface DiveDBConstraintAdviceTrait {
    Logger logger = LoggerFactory.getLogger(DiveDBConstraintAdviceTrait.class);

    @ExceptionHandler(DiveDBConstraintException.class)
    default ProblemDetail handleDiveDBConstraint(
            final DiveDBConstraintException exception, final WebRequest request) {
        logger.info(
                "DiveDBConstraintException while handling request to {}.",
                request.getDescription(false),
                exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.externalMessage());
    }
}
