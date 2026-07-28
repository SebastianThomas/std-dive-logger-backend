package ch.sthomas.stddivelogger.utils.advice;

import ch.sthomas.stddivelogger.model.exception.AbstractThrowableProblem;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Handles every {@link AbstractThrowableProblem} the same way, using its own status/detail. */
public interface ThrowableProblemAdviceTrait {

    @ExceptionHandler(AbstractThrowableProblem.class)
    default ProblemDetail handleAbstractThrowableProblem(final AbstractThrowableProblem problem) {
        return problem.toProblemDetail();
    }
}
